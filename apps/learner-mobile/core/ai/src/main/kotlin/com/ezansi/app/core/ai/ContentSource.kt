package com.ezansi.app.core.ai

/**
 * Identifies a content chunk that was used to ground an explanation.
 *
 * Every explanation produced by the [ExplanationEngine] includes a list
 * of content sources so the learner can see which curriculum material
 * informed the answer. This supports transparency ("here's where I
 * found this") and auditability (teachers can verify grounding).
 */
data class ContentSource(
    /** Unique chunk identifier within the pack. */
    val chunkId: String,
    /** Pack this chunk belongs to (e.g. "maths-grade6-caps"). */
    val packId: String,
    /** Human-readable title of the source chunk. */
    val title: String,
    /** Cosine similarity score from retrieval (0.0–1.0). */
    val relevanceScore: Float,
)
