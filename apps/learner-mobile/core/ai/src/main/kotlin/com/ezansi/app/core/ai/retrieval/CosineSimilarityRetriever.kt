package com.ezansi.app.core.ai.retrieval

import android.util.Log
import com.ezansi.app.core.data.contentpack.PackManager

/**
 * Pure-Kotlin content retriever using brute-force cosine similarity.
 *
 * Computes the cosine similarity between the query embedding and every
 * chunk embedding in the pack, then returns the top-K highest-scoring
 * chunks. This is the fallback retriever when FAISS JNI bindings are
 * not available.
 *
 * ## Performance
 *
 * For a typical content pack with ~200 chunks of 384 dimensions each,
 * brute-force cosine similarity takes < 1 ms on the target device.
 * This is well within the 10-second end-to-end latency budget (NF-01).
 * FAISS would only be needed for packs with thousands of chunks.
 *
 * ## Normalisation Assumption
 *
 * Both query and chunk embeddings are assumed to be L2-normalised.
 * When vectors are L2-normalised, cosine similarity equals the dot product,
 * which is what this implementation computes. This matches the FAISS
 * IndexFlatIP behaviour specified in ADR-0007.
 *
 * @param packManager Provides access to pack databases for reading embeddings.
 */
class CosineSimilarityRetriever(
    private val packManager: PackManager,
) : ContentRetriever {

    companion object {
        private const val TAG = "CosineSimilarityRetriever"
    }

    override suspend fun retrieve(
        queryEmbedding: FloatArray,
        packId: String,
        topK: Int,
    ): List<RetrievalResult> {
        val packDatabase = packManager.openPack(packId)
        if (packDatabase == null) {
            Log.w(TAG, "Cannot open pack '$packId' — pack not installed or corrupted")
            return emptyList()
        }

        // Read all pre-computed embeddings from the pack's SQLite database.
        // For ~200 chunks × 384 floats, this is ~300 KB — well within budget.
        val chunkEmbeddings = packDatabase.getAllEmbeddings()
        if (chunkEmbeddings.isEmpty()) {
            Log.w(TAG, "Pack '$packId' has no embeddings — cannot perform similarity search")
            return emptyList()
        }

        Log.d(TAG, "Computing cosine similarity against ${chunkEmbeddings.size} chunks in '$packId'")

        // Compute cosine similarity (= dot product for L2-normalised vectors)
        // and collect scored results.
        val scoredChunks = chunkEmbeddings.mapNotNull { (chunkId, chunkEmbedding) ->
            val score = computeDotProduct(queryEmbedding, chunkEmbedding)
            val chunk = packDatabase.getChunkById(chunkId)
            if (chunk != null) {
                RetrievalResult(
                    chunkId = chunkId,
                    score = score,
                    chunk = chunk.copy(relevanceScore = score),
                )
            } else {
                Log.w(TAG, "Embedding exists for chunk '$chunkId' but chunk data not found")
                null
            }
        }

        // Sort by descending similarity and take the top-K results.
        val topResults = scoredChunks
            .sortedByDescending { it.score }
            .take(topK)

        Log.d(
            TAG,
            "Retrieved top-${topResults.size} chunks from '$packId': " +
                topResults.joinToString { "${it.chunkId}(${String.format("%.3f", it.score)})" },
        )

        return topResults
    }

    /**
     * Computes the dot product of two vectors.
     *
     * For L2-normalised vectors, dot product equals cosine similarity.
     * This is the same operation that FAISS IndexFlatIP performs, ensuring
     * consistent ranking regardless of which retriever is used.
     */
    private fun computeDotProduct(vectorA: FloatArray, vectorB: FloatArray): Float {
        // Dimension mismatch is a programming error — fail fast
        require(vectorA.size == vectorB.size) {
            "Embedding dimension mismatch: query=${vectorA.size}, chunk=${vectorB.size}. " +
                "Both must be 384 (all-MiniLM-L6-v2)."
        }

        var dotProduct = 0.0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
        }
        return dotProduct
    }
}
