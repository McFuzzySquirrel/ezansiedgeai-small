package com.ezansi.app.feature.chat

/**
 * UI model for a single chat exchange displayed in the chat screen.
 *
 * Separates UI presentation from the persistence model ([ChatMessage][com.ezansi.app.core.data.chat.ChatMessage]).
 * Each instance represents one question–answer pair, with loading state
 * tracked during pipeline execution.
 */
data class ChatMessageUi(
    /** Unique message identifier (matches [ChatMessage.id]). */
    val id: String,
    /** The learner's question text. */
    val question: String,
    /** The AI-generated explanation, null while still generating. */
    val answer: String?,
    /** Content sources that grounded this explanation. */
    val sources: List<SourceUi>,
    /** True while the pipeline is actively processing this message. */
    val isLoading: Boolean,
    /** Current pipeline stage, drives the loading indicator label. */
    val pipelineState: PipelineState?,
    /** Timestamp of the interaction (epoch millis). */
    val timestamp: Long,
)

/**
 * UI model for a content source attribution chip.
 */
data class SourceUi(
    /** Human-readable title of the source content chunk. */
    val title: String,
    /** Relevance as a percentage (0–100) for display. */
    val relevancePercent: Int,
)

/**
 * Pipeline states shown to the learner during explanation generation.
 *
 * Each state maps to a Grade 4-readable label in the loading indicator.
 * The labels are defined in strings.xml for localisation.
 */
enum class PipelineState {
    /** Model is loading or initialising. */
    THINKING,
    /** Searching content packs for relevant material. */
    RETRIEVING,
    /** LLM is streaming the explanation text. */
    GENERATING,
    /** Pipeline encountered an error. */
    ERROR,
}
