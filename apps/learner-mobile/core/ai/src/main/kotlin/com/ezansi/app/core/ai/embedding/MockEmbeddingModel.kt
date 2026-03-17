package com.ezansi.app.core.ai.embedding

import android.util.Log
import kotlin.math.sqrt

/**
 * Mock embedding model for development and testing without native libraries.
 *
 * Produces deterministic 384-dimensional L2-normalised vectors based on
 * the input text hash. The same input always produces the same output,
 * making tests reproducible while exercising the full retrieval pipeline.
 *
 * ## Why This Exists
 *
 * The real [OnnxEmbeddingModel] requires ONNX Runtime Android native
 * libraries. This mock lets developers:
 * - Run the full embed → retrieve → prompt → generate pipeline
 * - Test UI integration without downloading model files
 * - Write unit tests for the pipeline without native dependencies
 *
 * ## Limitations
 *
 * Mock embeddings have no semantic meaning — similar questions will NOT
 * produce similar vectors. Retrieval results will be effectively random.
 * This is acceptable for pipeline integration testing but not for
 * evaluating retrieval quality.
 */
class MockEmbeddingModel : EmbeddingModel {

    companion object {
        private const val TAG = "MockEmbeddingModel"

        /** Matches all-MiniLM-L6-v2 output dimension. */
        const val EMBEDDING_DIMENSION = 384
    }

    private var loaded = false

    override suspend fun embed(text: String): FloatArray {
        check(loaded) {
            "Mock embedding model is not loaded. Call loadModel() first."
        }

        Log.d(TAG, "Generating mock embedding for text (${text.length} chars)")

        // Generate a deterministic vector from text content so the same
        // question always retrieves the same chunks during development.
        val embedding = FloatArray(EMBEDDING_DIMENSION)
        val normalised = text.lowercase().trim()

        for (i in 0 until EMBEDDING_DIMENSION) {
            val seed = "$normalised:dim$i"
            val hash = seed.hashCode()
            embedding[i] = hash.toFloat() / Int.MAX_VALUE.toFloat()
        }

        return l2Normalise(embedding)
    }

    override suspend fun loadModel(modelPath: String) {
        if (loaded) return
        Log.i(TAG, "Mock embedding model loaded (no actual model file needed)")
        loaded = true
    }

    override fun isLoaded(): Boolean = loaded

    override fun unload() {
        loaded = false
        Log.i(TAG, "Mock embedding model unloaded")
    }

    private fun l2Normalise(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (value in vector) {
            sumSquares += value * value
        }
        val magnitude = sqrt(sumSquares.toDouble()).toFloat()
        if (magnitude == 0.0f) return vector

        return FloatArray(vector.size) { i -> vector[i] / magnitude }
    }
}
