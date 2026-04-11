package com.ezansi.app.core.ai.search

import com.ezansi.app.core.data.ContentChunk

/**
 * A single search result from [ContentSearchEngine].
 *
 * Represents a content chunk matched by semantic similarity to a learner's
 * search query. Designed for display in the search results UI with enough
 * context for preview and navigation.
 *
 * @param chunkId Unique chunk identifier within its pack, for navigation.
 * @param title Display title from the content chunk.
 * @param snippet First ~100 characters of content for preview, truncated with "…".
 * @param topicPath CAPS topic path (e.g. "term1.fractions.addition").
 * @param score Cosine similarity score between query and chunk (0.0–1.0).
 * @param packId Which content pack this result came from.
 * @param chunk The full content chunk (available for "Ask AI" navigation).
 */
data class SearchResult(
    val chunkId: String,
    val title: String,
    val snippet: String,
    val topicPath: String,
    val score: Float,
    val packId: String,
    val chunk: ContentChunk,
)
