package com.ezansi.app.core.data.chat

/**
 * A single chat interaction between a learner and the explanation engine.
 *
 * Stored encrypted in the local SQLite database. The [question] and
 * [answer] fields contain the learner's query and the AI-generated
 * explanation. The [sources] list references content chunk IDs used
 * to generate the answer.
 *
 * **Privacy**: question, answer, and sources are encrypted at rest with
 * AES-256-GCM. They are never logged, transmitted, or included in analytics.
 */
data class ChatMessage(
    /** Unique message identifier (UUID). */
    val id: String,
    /** Profile that owns this message. */
    val profileId: String,
    /** The learner's question (encrypted at rest). */
    val question: String,
    /** The AI-generated explanation (encrypted at rest). */
    val answer: String,
    /** Content chunk IDs referenced in the answer (encrypted at rest). */
    val sources: List<String>,
    /** Timestamp of the interaction (epoch millis). */
    val timestamp: Long,
)
