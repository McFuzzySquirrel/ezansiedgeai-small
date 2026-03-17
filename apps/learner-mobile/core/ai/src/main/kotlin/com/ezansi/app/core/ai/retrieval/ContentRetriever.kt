package com.ezansi.app.core.ai.retrieval

import com.ezansi.app.core.data.ContentChunk

/**
 * Retrieves content chunks from a pack by semantic similarity to a query embedding.
 *
 * The retriever is the second step in the RAG pipeline:
 * question → embed → **retrieve** → prompt → generate.
 *
 * It compares the query embedding against all pre-computed chunk embeddings
 * in a content pack and returns the top-K most similar chunks, ranked by
 * cosine similarity score.
 *
 * Implementations:
 * - [CosineSimilarityRetriever]: Pure-Kotlin brute-force cosine similarity
 * - [FaissRetriever]: FAISS JNI-based retrieval (delegates to cosine for now)
 *
 * @see RetrievalResult for the ranked result structure
 */
interface ContentRetriever {

    /**
     * Retrieves the most semantically similar content chunks from a pack.
     *
     * @param queryEmbedding The L2-normalised 384-dimensional query vector.
     * @param packId The pack to search within (must be installed and opened).
     * @param topK Maximum number of results to return (default: 3).
     * @return Ranked list of [RetrievalResult]s, sorted by descending similarity.
     *         May return fewer than [topK] results if the pack has fewer chunks.
     *         Returns an empty list if the pack has no embeddings.
     */
    suspend fun retrieve(
        queryEmbedding: FloatArray,
        packId: String,
        topK: Int = 3,
    ): List<RetrievalResult>
}

/**
 * A single retrieval result: a content chunk with its similarity score.
 *
 * @param chunkId The unique identifier of the matched chunk.
 * @param score Cosine similarity between the query and chunk embedding (0.0–1.0).
 * @param chunk The full content chunk with title, content, and CAPS metadata.
 */
data class RetrievalResult(
    val chunkId: String,
    val score: Float,
    val chunk: ContentChunk,
)
