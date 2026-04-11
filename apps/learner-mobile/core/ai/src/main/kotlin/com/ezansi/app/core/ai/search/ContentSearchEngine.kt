package com.ezansi.app.core.ai.search

import com.ezansi.app.core.common.EzansiResult

/**
 * User-facing semantic search: embed query → retrieve → rank → return.
 * No LLM generation involved — this is a lightweight search-only flow.
 *
 * The search pipeline:
 * 1. Embed the learner's query using the on-device embedding model
 * 2. Search all installed content packs via [ContentRetriever]
 * 3. Merge results across packs and re-rank by similarity score
 * 4. Return the top-K results as [SearchResult]s
 *
 * This is intentionally separate from [ExplanationEngine] — search is fast
 * (<100 ms target, FT-NF-02) and does not consume LLM resources. Learners
 * can browse results and optionally tap "Ask AI" to generate an explanation
 * for a selected result.
 *
 * FT-FR-06: search(query, maxResults) → List<SearchResult>
 * FT-FR-07: Wraps EmbeddingModel + ContentRetriever for search-without-generation
 * FT-FR-11: Searches across all installed packs, merges and re-ranks
 *
 * @see ContentSearchEngineImpl for the implementation
 * @see SearchResult for the result structure
 */
interface ContentSearchEngine {

    /**
     * Search all installed content packs for chunks matching the query.
     *
     * Embeds the query into a vector, retrieves similar chunks from every
     * installed pack, merges results, and returns the top [maxResults] ranked
     * by descending cosine similarity score.
     *
     * @param query Natural language search query (e.g. "how to share things equally").
     * @param maxResults Maximum number of results to return (default: 10).
     * @return [EzansiResult.Success] with ranked results (may be empty if no matches),
     *         or [EzansiResult.Error] if the query is blank or the embedding model
     *         is not loaded.
     */
    suspend fun search(
        query: String,
        maxResults: Int = 10,
    ): EzansiResult<List<SearchResult>>

    /**
     * Whether the search engine is ready (embedding model loaded).
     *
     * The UI should check this before showing the search affordance.
     * If false, the search screen can show a "loading model" state.
     */
    fun isReady(): Boolean
}
