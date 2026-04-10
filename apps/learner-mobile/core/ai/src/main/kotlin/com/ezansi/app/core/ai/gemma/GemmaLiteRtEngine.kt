package com.ezansi.app.core.ai.gemma

import android.util.Log
import com.ezansi.app.core.ai.inference.LlmEngine
import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Gemma 4 LLM engine powered by MediaPipe GenAI SDK via [GemmaModelProvider].
 *
 * This engine delegates model lifecycle (load, unload, instance access) to a
 * shared [GemmaModelProvider], which holds a single MediaPipe `LlmInference`
 * instance used for both generation and embedding. The provider is the lifecycle
 * owner — this engine **never** unloads the underlying model because the
 * [GemmaEmbeddingModel][com.ezansi.app.core.ai.embedding.EmbeddingModel] may
 * still need it.
 *
 * ## Stub Mode
 *
 * When MediaPipe is unavailable (JVM unit tests, emulator without native libs),
 * [GemmaModelProvider.getInference] returns `null`. In this case, [generate]
 * emits a deterministic stub response so the full pipeline can be exercised
 * without a real model.
 *
 * ## Inference Contract
 *
 * - **Temperature / topK / seed** are configured at model-load time via
 *   [GemmaModelConfig] and enforced by MediaPipe internally.
 * - **maxTokens** is enforced by word-level truncation of the response.
 * - **30-second wall-time cap** (AI-09) is enforced via [withTimeoutOrNull].
 * - Generation uses the synchronous `LlmInference.generateResponse(prompt)`
 *   API accessed via reflection (since the class is typed as `Any?`).
 *
 * @param modelProvider Shared Gemma 4 lifecycle manager — injected by DI.
 * @param config        Default configuration. [loadModel]'s `modelPath`
 *                      parameter overrides [GemmaModelConfig.modelPath].
 *
 * @see GemmaModelProvider
 * @see LlmEngine
 */
class GemmaLiteRtEngine(
    private val modelProvider: GemmaModelProvider,
    private val config: GemmaModelConfig = GemmaModelConfig(modelPath = "placeholder"),
) : LlmEngine {

    companion object {
        private const val TAG = "GemmaLiteRtEngine"

        /** Wall-time cap for the generation phase (AI-09). */
        private val GENERATION_TIMEOUT = 30.seconds

        /** Maximum characters from the prompt echoed in stub output. */
        private const val STUB_PROMPT_PREVIEW_LENGTH = 50
    }

    // ── LlmEngine implementation ────────────────────────────────────

    /**
     * Loads the Gemma 4 model via the shared [GemmaModelProvider].
     *
     * A [GemmaModelConfig] is built by copying the constructor [config]
     * with [modelPath] overridden. The provider handles idempotent reloads,
     * GPU → CPU fallback, and stub-mode lifecycle.
     *
     * @param modelPath Absolute path to the Gemma 4 `.task` model file.
     * @throws java.io.FileNotFoundException if the file does not exist.
     * @throws IllegalStateException if the model cannot be loaded.
     */
    override suspend fun loadModel(modelPath: String) {
        Log.i(TAG, "loadModel requested: $modelPath")
        val loadConfig = config.copy(modelPath = modelPath)
        modelProvider.loadModel(loadConfig)
        Log.i(TAG, "Model loaded via provider (mode: ${modelProvider.runtimeMode()})")
    }

    /**
     * Returns `true` if the shared provider has a model loaded and ready.
     */
    override fun isLoaded(): Boolean = modelProvider.isLoaded()

    /**
     * Generates text from [prompt] using the loaded Gemma 4 model.
     *
     * The returned [Flow] emits the generated text. In real-MediaPipe mode
     * the full response is emitted as a single string (the MediaPipe sync
     * API returns the complete generation). In stub mode a deterministic
     * preview of the prompt is emitted.
     *
     * Generation is bounded by:
     * - [maxTokens] — response is word-truncated to this limit
     * - 30-second wall-time cap (AI-09) — returns a timeout message
     *
     * @param prompt    The fully-assembled RAG prompt.
     * @param maxTokens Maximum tokens (approximated as words) to emit.
     * @return A [Flow] of generated text chunks.
     * @throws IllegalStateException if the model is not loaded.
     */
    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        check(modelProvider.isLoaded()) {
            "Gemma 4 model is not loaded. Call loadModel() before generate()."
        }

        modelProvider.setMode(GemmaModelProvider.ModelMode.GENERATION)
        Log.d(TAG, "generate() called (prompt length: ${prompt.length}, maxTokens: $maxTokens)")

        val inference = modelProvider.getInference()

        if (inference == null) {
            // Stub mode — produce deterministic output for testing
            val preview = prompt.take(STUB_PROMPT_PREVIEW_LENGTH)
            val stubResponse = "Gemma 4 stub: $preview..."
            Log.d(TAG, "Stub mode — emitting deterministic response")
            emit(truncateToMaxTokens(stubResponse, maxTokens))
            return@flow
        }

        // Real MediaPipe inference via reflection
        val result = withTimeoutOrNull(GENERATION_TIMEOUT) {
            invokeGenerateResponse(inference, prompt)
        }

        if (result == null) {
            Log.w(TAG, "Generation timed out after $GENERATION_TIMEOUT (AI-09)")
            emit("[Generation timed out after ${GENERATION_TIMEOUT.inWholeSeconds}s]")
            return@flow
        }

        Log.d(TAG, "Generation complete (${result.length} chars)")
        emit(truncateToMaxTokens(result, maxTokens))
    }.flowOn(Dispatchers.IO)

    /**
     * Releases this engine's claim on the shared model.
     *
     * Because the underlying model is **shared** with [GemmaEmbeddingModel]
     * via [GemmaModelProvider], this method does **not** unload the model.
     * It only sets the provider's mode to [GemmaModelProvider.ModelMode.IDLE]
     * to signal that generation is no longer in progress. The actual model
     * unload is the responsibility of whoever owns the provider's lifecycle
     * (typically `AppContainer`).
     */
    override fun unload() {
        Log.i(TAG, "unload() — setting mode to IDLE (shared model not unloaded)")
        modelProvider.setMode(GemmaModelProvider.ModelMode.IDLE)
    }

    /**
     * Maps the provider's [GemmaRuntimeMode] to the engine-level [LlmRuntimeMode].
     */
    override fun runtimeMode(): LlmRuntimeMode = when (modelProvider.runtimeMode()) {
        GemmaRuntimeMode.REAL_MEDIAPIPE -> LlmRuntimeMode.REAL_MEDIAPIPE
        GemmaRuntimeMode.MEDIAPIPE_UNAVAILABLE -> LlmRuntimeMode.NATIVE_UNAVAILABLE
        GemmaRuntimeMode.STUB -> LlmRuntimeMode.MOCK
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Calls `LlmInference.generateResponse(prompt)` via reflection.
     *
     * The inference handle is typed as `Any?` to avoid a hard compile-time
     * dependency on the MediaPipe SDK in JVM unit tests. At runtime on-device
     * the object is a `com.google.mediapipe.tasks.genai.llminference.LlmInference`.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun invokeGenerateResponse(inference: Any, prompt: String): String {
        return try {
            val method = inference.javaClass.getMethod(
                "generateResponse",
                String::class.java,
            )
            val response = method.invoke(inference, prompt) as? String
            response ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Reflection call to generateResponse failed", e)
            "[Error: generation failed — ${e.message}]"
        }
    }

    /**
     * Truncates [text] to approximately [maxTokens] words.
     *
     * LLM tokens are sub-word units, but for the purpose of enforcing
     * the generation budget in a backend-agnostic way, we approximate
     * 1 token ≈ 1 word (split on whitespace). This is a conservative
     * estimate — real BPE produces more tokens than words.
     */
    private fun truncateToMaxTokens(text: String, maxTokens: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= maxTokens) return text
        return words.take(maxTokens).joinToString(" ")
    }
}
