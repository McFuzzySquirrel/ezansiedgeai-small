package com.ezansi.app.core.ai

import android.util.Log
import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import com.ezansi.app.core.ai.inference.LlmEngine
import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import com.ezansi.app.core.ai.prompt.PromptBuilder
import com.ezansi.app.core.ai.retrieval.ContentRetriever
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.common.StorageManager
import com.ezansi.app.core.common.getOrNull
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.LearnerPreference
import com.ezansi.app.core.data.PreferenceRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Default implementation of [ExplanationEngine] — the sole public API of the AI layer.
 *
 * Orchestrates the full retrieval-augmented generation pipeline:
 * 1. **Thinking** — Lazy-load models if not already in memory
 * 2. **Embedding** — Convert the learner's question into a 384-dim vector
 * 3. **Retrieving** — Find the top-3 most relevant content chunks via cosine similarity
 * 4. **Prompt building** — Construct a grounded Qwen2.5 ChatML prompt
 * 5. **Generating** — Stream tokens from the on-device LLM
 * 6. **Complete** — Return the full explanation with source attribution
 *
 * ## Sequential Model Loading (AI-08)
 *
 * To stay within the 2 GB RAM budget (NF-02), models are loaded sequentially:
 * 1. Load embedding model (~87 MB) → embed question → unload embedding model
 * 2. Load LLM (~1,066 MB) → generate explanation → keep LLM loaded for follow-ups
 *
 * In practice, on subsequent queries the LLM is already loaded, so only the
 * embedding model needs to be loaded/unloaded per question.
 *
 * ## Error Handling
 *
 * Every failure path emits [ExplanationResult.Error] with a human-readable
 * message suitable for display in the UI. Internal exceptions are logged
 * but never exposed to the learner.
 *
 * ## Thread Safety
 *
 * All heavy computation runs on [DispatcherProvider.io] (embedding, retrieval,
 * inference). The returned Flow can be collected from any dispatcher.
 *
 * @param embeddingModel The on-device embedding model (all-MiniLM-L6-v2).
 * @param contentRetriever Retrieves chunks by similarity from content packs.
 * @param promptBuilder Constructs grounded prompts from retrieved context.
 * @param llmEngine The on-device LLM for generating explanations.
 * @param contentPackRepository Provides list of installed content packs.
 * @param preferenceRepository Provides learner preferences for prompt customisation.
 * @param storageManager Resolves model file paths on local storage.
 * @param dispatcherProvider Coroutine dispatchers for background work.
 */
class ExplanationEngineImpl(
    private val embeddingModel: EmbeddingModel,
    private val contentRetriever: ContentRetriever,
    private val promptBuilder: PromptBuilder,
    private val llmEngine: LlmEngine,
    private val contentPackRepository: ContentPackRepository,
    private val preferenceRepository: PreferenceRepository,
    private val storageManager: StorageManager,
    private val dispatcherProvider: DispatcherProvider,
) : ExplanationEngine {

    companion object {
        private const val TAG = "ExplanationEngine"

        /** Top-K chunks to retrieve per content pack. */
        private const val RETRIEVAL_TOP_K = 3

        /** Maximum tokens for LLM generation per response (AI-04). */
        private const val MAX_GENERATION_TOKENS = 548

        /** Wall-time cap for the entire pipeline in milliseconds (AI-09). */
        private const val PIPELINE_TIMEOUT_MS = 30_000L

        /** Minimum similarity score for a chunk to be considered relevant. */
        private const val MIN_RELEVANCE_THRESHOLD = 0.05f

        /** Expected embedding model filename in the models directory. */
        private const val EMBEDDING_MODEL_FILENAME = "all-MiniLM-L6-v2.onnx"

        /** Expected LLM model filename in the models directory. */
        private const val LLM_MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }

    override fun explain(question: String, profileId: String): Flow<ExplanationResult> = flow {
        val startTimeMs = System.currentTimeMillis()

        try {
            withTimeout(PIPELINE_TIMEOUT_MS) {
                executePipeline(question, profileId, startTimeMs, this@flow)
            }
        } catch (e: TimeoutCancellationException) {
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            Log.e(TAG, "Pipeline timed out after ${elapsedMs}ms")
            emit(
                ExplanationResult.Error(
                    "The answer is taking too long. Please try asking a simpler question.",
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected pipeline error", e)
            emit(
                ExplanationResult.Error("Something went wrong. Please try again."),
            )
        }
    }.flowOn(dispatcherProvider.io)

    // ── Pipeline stages ─────────────────────────────────────────────

    /**
     * Executes the full RAG pipeline, emitting state transitions as it progresses.
     *
     * This method orchestrates the four pipeline stages in sequence,
     * with error handling at each stage to produce user-friendly messages.
     */
    private suspend fun executePipeline(
        question: String,
        profileId: String,
        startTimeMs: Long,
        collector: kotlinx.coroutines.flow.FlowCollector<ExplanationResult>,
    ) {
        // ── Stage 1: Thinking — load models if needed ───────────────
        collector.emit(ExplanationResult.Thinking)
        Log.i(TAG, "Pipeline started for question (${question.length} chars)")

        ensureEmbeddingModelLoaded()
        emitRuntimeStatus(collector)

        // ── Stage 2: Embed & Retrieve ───────────────────────────────
        collector.emit(ExplanationResult.Retrieving)

        val queryEmbedding = withContext(dispatcherProvider.default) {
            embeddingModel.embed(question)
        }
        Log.d(TAG, "Question embedded in ${System.currentTimeMillis() - startTimeMs}ms")

        val retrievedChunks = retrieveFromAllInstalledPacks(queryEmbedding)

        if (retrievedChunks.isEmpty()) {
            handleEmptyRetrieval(collector)
            return
        }

        Log.d(
            TAG,
            "Retrieved ${retrievedChunks.size} chunks in " +
                "${System.currentTimeMillis() - startTimeMs}ms",
        )

        // ── Stage 3: Unload embedding, load LLM ────────────────────
        // Sequential model loading (AI-08): free embedding RAM before loading LLM
        unloadEmbeddingModelIfLlmNotLoaded()
        ensureLlmModelLoaded()
        emitRuntimeStatus(collector)

        // ── Stage 4: Build prompt with learner preferences ──────────
        val preferences = loadPreferencesForProfile(profileId)
        val prompt = promptBuilder.buildGroundedPromptFromRetrievedChunks(
            question = question,
            retrievedChunks = retrievedChunks,
            preferences = preferences,
        )

        Log.d(
            TAG,
            "Prompt built: ${prompt.length} chars (~${promptBuilder.estimateTokenCount(prompt)} tokens)",
        )

        // ── Stage 5: Generate explanation ───────────────────────────
        val fullResponse = StringBuilder()

        llmEngine.generate(prompt, MAX_GENERATION_TOKENS).collect { token ->
            fullResponse.append(token)
            collector.emit(ExplanationResult.Generating(fullResponse.toString()))
        }

        // ── Stage 6: Complete with source attribution ───────────────
        val sources = retrievedChunks.map { result ->
            ContentSource(
                chunkId = result.chunkId,
                packId = result.chunk.packId,
                title = result.chunk.title,
                relevanceScore = result.score,
            )
        }

        val elapsedMs = System.currentTimeMillis() - startTimeMs
        Log.i(TAG, "Pipeline complete in ${elapsedMs}ms — ${fullResponse.length} chars generated")

        collector.emit(
            ExplanationResult.Complete(
                fullText = fullResponse.toString(),
                sources = sources,
            ),
        )
    }

    private suspend fun emitRuntimeStatus(
        collector: kotlinx.coroutines.flow.FlowCollector<ExplanationResult>,
    ) {
        val embeddingMode = embeddingModel.runtimeMode()
        val llmMode = llmEngine.runtimeMode()

        val embeddingMessage = when (embeddingMode) {
            EmbeddingRuntimeMode.REAL_ONNX -> "Real ONNX path active"
            EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK -> "ONNX fallback deterministic embedding path"
            EmbeddingRuntimeMode.MOCK -> "Mock embedding path active"
            EmbeddingRuntimeMode.UNKNOWN -> "Embedding runtime mode unknown"
        }

        val llmMessage = when (llmMode) {
            LlmRuntimeMode.REAL_NATIVE -> "Llama native runtime path active"
            LlmRuntimeMode.NATIVE_UNAVAILABLE -> "Llama native unavailable path"
            LlmRuntimeMode.MOCK -> "Mock LLM path active"
            LlmRuntimeMode.UNKNOWN -> "LLM runtime mode unknown"
        }

        val message = "$embeddingMessage; $llmMessage"

        if (embeddingMode == EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK ||
            llmMode == LlmRuntimeMode.NATIVE_UNAVAILABLE
        ) {
            Log.w(TAG, message)
        } else {
            Log.i(TAG, message)
        }

        collector.emit(
            ExplanationResult.RuntimeStatus(
                embeddingMode = embeddingMode,
                llmMode = llmMode,
                message = message,
            ),
        )
    }

    // ── Model lifecycle ─────────────────────────────────────────────

    /**
     * Loads the embedding model lazily on first use.
     *
     * If the model file does not exist, throws with a user-friendly message.
     * The model path is resolved from [StorageManager.getModelsDir].
     */
    private suspend fun ensureEmbeddingModelLoaded() {
        if (embeddingModel.isLoaded()) return

        val modelPath = resolveModelPath(EMBEDDING_MODEL_FILENAME)
        Log.i(TAG, "Lazy-loading embedding model from: $modelPath")

        try {
            embeddingModel.loadModel(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load embedding model", e)
            throw ModelLoadException(
                "The AI model needs to be downloaded first. " +
                    "Please check the models directory.",
                e,
            )
        }
    }

    /**
     * Loads the LLM lazily on first use.
     *
     * Follows the sequential loading pattern (AI-08): the embedding model
     * should be unloaded before calling this to stay within RAM budget.
     */
    private suspend fun ensureLlmModelLoaded() {
        if (llmEngine.isLoaded()) return

        val modelPath = resolveModelPath(LLM_MODEL_FILENAME)
        Log.i(TAG, "Lazy-loading LLM from: $modelPath")

        try {
            llmEngine.loadModel(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM", e)
            throw ModelLoadException(
                "The AI model needs to be downloaded first. " +
                    "Please check the models directory.",
                e,
            )
        }
    }

    /**
     * Unloads the embedding model to free RAM before loading the LLM.
     *
     * Only unloads if the LLM is not already loaded — when the LLM is
     * cached from a previous query, we skip the unload/reload cycle.
     */
    private fun unloadEmbeddingModelIfLlmNotLoaded() {
        if (!llmEngine.isLoaded() && embeddingModel.isLoaded()) {
            Log.i(TAG, "Unloading embedding model to free RAM for LLM (AI-08)")
            embeddingModel.unload()
        }
    }

    // ── Retrieval ───────────────────────────────────────────────────

    /**
     * Searches all installed content packs for chunks relevant to the query.
     *
     * Retrieves top-K chunks from each pack, then merges and re-ranks
     * across all packs to get the globally top-K chunks.
     *
     * @return Merged list of top-K chunks across all packs, sorted by score.
     */
    private suspend fun retrieveFromAllInstalledPacks(
        queryEmbedding: FloatArray,
    ): List<RetrievalResult> {
        val packsResult = contentPackRepository.getInstalledPacks()

        val packs = when (packsResult) {
            is EzansiResult.Success -> packsResult.data
            is EzansiResult.Error -> {
                Log.e(TAG, "Failed to list installed packs: ${packsResult.message}")
                emptyList()
            }
            is EzansiResult.Loading -> emptyList()
        }

        if (packs.isEmpty()) {
            Log.w(TAG, "No content packs installed")
            return emptyList()
        }

        Log.d(TAG, "Searching ${packs.size} installed pack(s) for relevant content")

        // Retrieve from each pack and merge results
        val allResults = mutableListOf<RetrievalResult>()
        for (pack in packs) {
            try {
                val packResults = contentRetriever.retrieve(
                    queryEmbedding = queryEmbedding,
                    packId = pack.packId,
                    topK = RETRIEVAL_TOP_K,
                )
                allResults.addAll(packResults)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve from pack '${pack.packId}', skipping", e)
            }
        }

        // Re-rank across all packs and filter by minimum relevance
        return allResults
            .filter { it.score >= MIN_RELEVANCE_THRESHOLD }
            .sortedByDescending { it.score }
            .take(RETRIEVAL_TOP_K)
    }

    // ── Preference loading ──────────────────────────────────────────

    /**
     * Loads learner preferences for prompt customisation.
     *
     * Returns null if preferences cannot be loaded (new profile, error).
     * The [PromptBuilder] uses safe defaults when preferences are null.
     */
    private suspend fun loadPreferencesForProfile(
        profileId: String,
    ): List<LearnerPreference>? {
        return try {
            preferenceRepository.getPreferences(profileId).getOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load preferences for profile '$profileId', using defaults", e)
            null
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    /**
     * Handles the case where no relevant content was found across all packs.
     *
     * This can happen when:
     * - No content packs are installed
     * - No chunks have high enough similarity to the question
     * - The question is about a topic not covered in installed packs
     */
    private suspend fun handleEmptyRetrieval(
        collector: kotlinx.coroutines.flow.FlowCollector<ExplanationResult>,
    ) {
        // Check if any packs are installed to give a more specific error
        val packsResult = contentPackRepository.getInstalledPacks()
        val hasPacks = packsResult is EzansiResult.Success && packsResult.data.isNotEmpty()

        val message = if (!hasPacks) {
            "No content packs installed. Please install a content pack to get started."
        } else {
            "I couldn't find relevant content for that question. " +
                "Try asking about a specific Grade 6 Maths topic."
        }

        Log.w(TAG, "Empty retrieval: $message")
        collector.emit(ExplanationResult.Error(message))
    }

    // ── Utilities ───────────────────────────────────────────────────

    /**
     * Resolves the absolute path to a model file in the models directory.
     */
    private fun resolveModelPath(filename: String): String {
        return java.io.File(storageManager.getModelsDir(), filename).absolutePath
    }
}

/**
 * Internal exception for model loading failures.
 *
 * Carries a user-friendly message that can be emitted as [ExplanationResult.Error].
 */
private class ModelLoadException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
