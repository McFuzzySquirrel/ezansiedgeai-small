package com.ezansi.app.core.ai.search

import android.util.Log
import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.retrieval.ContentRetriever
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.common.EzansiResult

/**
 * Implementation of [ContentSearchEngine] — search-without-generation.
 *
 * Orchestrates the embed → retrieve → merge → rank flow across all installed
 * content packs. No LLM is loaded or invoked; this keeps search fast (<100 ms
 * target, FT-NF-02) and memory-light.
 *
 * FT-FR-07: Wraps EmbeddingModel + ContentRetriever for search-only queries.
 * FT-FR-11: Searches across all installed packs, merges and re-ranks.
 *
 * @param embeddingModel The on-device embedding model for query vectorisation.
 * @param contentRetriever The retriever for semantic chunk lookup within a pack.
 * @param getInstalledPackIds Provides the list of currently installed pack IDs.
 *        Accepts a lambda to avoid coupling to the data layer's repository directly.
 * @param ensureModelLoaded Triggers lazy model loading when the embedding model
 *        is not yet loaded. Decouples the search engine from model path resolution.
 */
class ContentSearchEngineImpl(
    private val embeddingModel: EmbeddingModel,
    private val contentRetriever: ContentRetriever,
    private val getInstalledPackIds: suspend () -> List<String>,
    private val ensureModelLoaded: suspend () -> Unit = {},
) : ContentSearchEngine {

    override suspend fun search(
        query: String,
        maxResults: Int,
    ): EzansiResult<List<SearchResult>> {
        // 1. Validate query
        if (query.isBlank()) {
            return EzansiResult.Error("Search query cannot be empty.")
        }

        // 2. Ensure embedding model is loaded (lazy load on first search)
        if (!embeddingModel.isLoaded()) {
            try {
                Log.i(TAG, "Embedding model not loaded — triggering lazy load for search")
                ensureModelLoaded()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load embedding model for search", e)
                return EzansiResult.Error(
                    "Search is not available yet. The AI model could not be loaded.",
                    cause = e,
                )
            }

            if (!embeddingModel.isLoaded()) {
                return EzansiResult.Error(
                    "Search is not available yet. The embedding model is still loading.",
                )
            }
        }

        return try {
            // 3. Embed the query
            val queryEmbedding = embeddingModel.embed(query)

            // 4. Retrieve from all installed packs
            val packIds = getInstalledPackIds()
            val allResults = mutableListOf<RetrievalResult>()
            for (packId in packIds) {
                val packResults = contentRetriever.retrieve(
                    queryEmbedding = queryEmbedding,
                    packId = packId,
                    topK = maxResults,
                )
                allResults.addAll(packResults)
            }

            // 5. Merge, re-rank by score descending, take top maxResults
            val ranked = allResults
                .sortedByDescending { it.score }
                .take(maxResults)

            // 6. Map RetrievalResult → SearchResult
            val searchResults = ranked.map { it.toSearchResult() }

            EzansiResult.Success(searchResults)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query (${query.length} chars)", e)
            EzansiResult.Error(
                "Search failed. Please try again.",
                cause = e,
            )
        }
    }

    override fun isReady(): Boolean = embeddingModel.isLoaded()

    companion object {
        private const val TAG = "ContentSearchEngine"

        /** Maximum snippet length before truncation. */
        internal const val SNIPPET_MAX_LENGTH = 100
    }
}

/**
 * Converts a [RetrievalResult] to a [SearchResult] for UI display.
 *
 * Generates a snippet by taking the first ~100 characters of the chunk
 * content, trimming to the last word boundary, and appending "…" if
 * the content was truncated.
 */
internal fun RetrievalResult.toSearchResult(): SearchResult {
    val content = chunk.content
    val snippet = generateSnippet(content)
    return SearchResult(
        chunkId = chunkId,
        title = chunk.title,
        snippet = snippet,
        topicPath = chunk.topicPath,
        score = score,
        packId = chunk.packId,
        chunk = chunk,
    )
}

/**
 * Generates a display snippet from content text.
 *
 * If the content exceeds [ContentSearchEngineImpl.SNIPPET_MAX_LENGTH] characters,
 * truncates to the last whitespace boundary before the limit and appends "…".
 * Short content is returned as-is.
 */
internal fun generateSnippet(content: String): String {
    val trimmed = content.trim()
    if (trimmed.length <= ContentSearchEngineImpl.SNIPPET_MAX_LENGTH) {
        return trimmed
    }
    // Find last whitespace at or before the limit to avoid cutting mid-word
    val cutoff = trimmed.lastIndexOf(' ', ContentSearchEngineImpl.SNIPPET_MAX_LENGTH)
    val end = if (cutoff > 0) cutoff else ContentSearchEngineImpl.SNIPPET_MAX_LENGTH
    return trimmed.substring(0, end) + "…"
}
