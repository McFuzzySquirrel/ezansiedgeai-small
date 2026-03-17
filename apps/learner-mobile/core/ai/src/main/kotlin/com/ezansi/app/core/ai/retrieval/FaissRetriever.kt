package com.ezansi.app.core.ai.retrieval

import android.util.Log
import com.ezansi.app.core.data.contentpack.PackManager

/**
 * FAISS-based content retriever using IndexFlatIP (inner product / cosine).
 *
 * ## Current Status: Delegates to [CosineSimilarityRetriever]
 *
 * FAISS JNI bindings for Android are not yet integrated. This class
 * provides the interface entry point for when FAISS is available, but
 * currently delegates all retrieval to [CosineSimilarityRetriever].
 *
 * For packs with ~200 chunks, brute-force cosine similarity is fast
 * enough (< 1 ms). FAISS becomes necessary only when packs grow to
 * thousands of chunks, where approximate nearest-neighbour search
 * would provide meaningful speedup.
 *
 * ## Future FAISS Integration
 *
 * When FAISS JNI bindings are available, this class will:
 * 1. Read the serialised FAISS index from `PackDatabase.getFaissIndex()`
 * 2. Write it to a temporary file (FAISS C++ requires file-based loading)
 * 3. Load the index via JNI: `faiss_read_index(tempFile.path)`
 * 4. Query with `index.search(queryVector, topK)` to get (distances, indices)
 * 5. Map FAISS indices back to chunk_ids using `PackDatabase.getFaissChunkOrder()`
 * 6. Return [RetrievalResult]s with the chunk data and similarity scores
 *
 * @param packManager Provides access to pack databases for reading indexes.
 * @see CosineSimilarityRetriever for the current fallback implementation
 */
class FaissRetriever(
    private val packManager: PackManager,
) : ContentRetriever {

    companion object {
        private const val TAG = "FaissRetriever"
    }

    // Delegate to pure-Kotlin cosine similarity until FAISS JNI is integrated
    private val fallbackRetriever = CosineSimilarityRetriever(packManager)

    override suspend fun retrieve(
        queryEmbedding: FloatArray,
        packId: String,
        topK: Int,
    ): List<RetrievalResult> {
        val packDatabase = packManager.openPack(packId)

        // Check if a serialised FAISS index exists in this pack
        val faissIndexData = packDatabase?.getFaissIndex()
        if (faissIndexData != null) {
            Log.d(
                TAG,
                "FAISS index found in pack '$packId' (${faissIndexData.size} bytes) " +
                    "but JNI bindings not yet available — falling back to cosine similarity",
            )
            // TODO(ai-pipeline-engineer): Load FAISS index via JNI when available:
            // 1. Write faissIndexData to a temp file
            // 2. val index = FaissIndex.read(tempFile.path)
            // 3. val (distances, ids) = index.search(queryEmbedding, topK)
            // 4. Map ids to chunk_ids via getFaissChunkOrder()
        }

        return fallbackRetriever.retrieve(queryEmbedding, packId, topK)
    }
}
