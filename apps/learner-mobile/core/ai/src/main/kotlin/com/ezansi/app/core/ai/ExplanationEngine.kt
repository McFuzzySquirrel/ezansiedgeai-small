package com.ezansi.app.core.ai

import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import kotlinx.coroutines.flow.Flow

/**
 * Core AI interface: embed → retrieve → prompt → generate.
 *
 * The ExplanationEngine orchestrates the full RAG pipeline:
 * 1. Embed the learner's question using the on-device embedding model
 * 2. Retrieve relevant content chunks via FAISS similarity search
 * 3. Construct a grounded prompt using Jinja2-style templates
 * 4. Generate a streaming explanation via the on-device LLM (llama.cpp)
 *
 * The engine emits a [Flow] of [ExplanationResult] states so the UI can
 * show progressive feedback: "Thinking…" → "Finding relevant content…" →
 * streaming text → complete answer with sources.
 *
 * Implementors (ai-pipeline-engineer) must ensure:
 * - All inference runs on-device with no network calls
 * - Models are loaded lazily on first query, not at app startup
 * - Models are unloaded when the app is backgrounded
 * - Explanations are always grounded in retrieved content (no hallucination)
 *
 * @see ExplanationResult for the state machine emitted by [explain]
 * @see ContentSource for source attribution
 */
interface ExplanationEngine {

    /**
     * Generates a curriculum-grounded explanation for the learner's question.
     *
     * @param question The learner's question in natural language.
     * @param profileId The active profile ID, used to load preferences
     *                  (reading level, explanation style) for prompt customisation.
     * @return A [Flow] that emits [ExplanationResult] states as the pipeline progresses.
     *         The flow completes after emitting [ExplanationResult.Complete] or
     *         [ExplanationResult.Error].
     */
    fun explain(question: String, profileId: String): Flow<ExplanationResult>
}

/**
 * State machine for the explanation pipeline.
 *
 * Emitted in order: [Thinking] → [Retrieving] → [Generating]* → [Complete]
 * Any state may be followed by [Error] if the pipeline fails.
 */
sealed class ExplanationResult {

    /** Pipeline is initialising: loading models if needed. */
    data object Thinking : ExplanationResult()

    /** Embedding the question and searching content packs for relevant chunks. */
    data object Retrieving : ExplanationResult()

    /**
     * Runtime mode signal for observability in emulator/mobile validation.
     */
    data class RuntimeStatus(
        val embeddingMode: EmbeddingRuntimeMode,
        val llmMode: LlmRuntimeMode,
        val message: String,
    ) : ExplanationResult()

    /**
     * LLM is generating text. Emitted multiple times as tokens stream in.
     * @param partialText The accumulated text generated so far.
     */
    data class Generating(val partialText: String) : ExplanationResult()

    /**
     * Pipeline completed successfully.
     * @param fullText The complete explanation text.
     * @param sources Content chunks that grounded this explanation.
     */
    data class Complete(
        val fullText: String,
        val sources: List<ContentSource>,
    ) : ExplanationResult()

    /**
     * Pipeline failed with a human-readable error message.
     * @param message Plain-language description of what went wrong
     *                (e.g. "No content packs installed for this subject").
     */
    data class Error(val message: String) : ExplanationResult()
}
