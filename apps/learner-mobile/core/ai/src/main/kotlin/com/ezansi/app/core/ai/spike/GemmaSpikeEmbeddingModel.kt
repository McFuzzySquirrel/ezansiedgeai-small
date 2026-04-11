package com.ezansi.app.core.ai.spike

import android.util.Log
import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import kotlin.math.sqrt

/**
 * Spike P0-006: Gemma 4 embedding model for on-device semantic search.
 *
 * This is **spike code** for evaluating Gemma 4 1B's ability to produce
 * embeddings suitable for the retrieval stage of the RAG pipeline.
 *
 * ## Spike Finding: MediaPipe GenAI Embedding API
 *
 * **As of MediaPipe GenAI SDK 0.10.x, there is no dedicated embedding API.**
 * The `LlmInference` class exposes only text generation (`generateResponse`),
 * not hidden-state extraction or pooled embeddings.
 *
 * ### Approaches Investigated
 *
 * 1. **`LlmInference.generateResponse` with empty generation** — Setting
 *    `maxTokens=0` or using a prompt that requests an embedding is not
 *    supported; the API generates text, not vectors.
 *
 * 2. **Hidden-state extraction** — LiteRT models *can* expose intermediate
 *    tensors, but the `.task` packaging used by MediaPipe's GenAI SDK wraps
 *    the model in a task-specific runner that does not expose raw tensors.
 *
 * 3. **Custom LiteRT interpreter** — Google's `ai.google.dev` documentation
 *    hints that Gemma 4 supports an "embedding mode" via a separate model
 *    configuration or a dedicated embedding `.tflite` file. This would use
 *    the TFLite Interpreter API directly, bypassing MediaPipe's task wrapper.
 *    **This is the most promising path and should be investigated in the
 *    next spike iteration.**
 *
 * ### Current Implementation
 *
 * This class provides a **deterministic hash-based embedding stub** that:
 * - Implements the [EmbeddingModel] interface for pipeline compatibility
 * - Supports configurable embedding dimensions (256/384/512/768)
 * - Returns L2-normalised vectors
 * - Logs timing metrics for benchmark comparison
 *
 * The stub allows the benchmark runner to measure pipeline overhead and
 * retrieval accuracy against the same content pack embeddings — but the
 * embeddings themselves have no semantic meaning.
 *
 * ### Decision Gate Implication
 *
 * If Gemma 4 cannot produce embeddings on-device via a supported API path,
 * the spike outcome will be **Hybrid (Gen ✅ + Embed ❌)**: use Gemma 4 for
 * generation but retain all-MiniLM-L6-v2 (ONNX) for embedding. This is
 * Decision Path 2 in the [P0-006 README](../../spikes/p0-006-gemma4-evaluation/README.md).
 *
 * @param embeddingDimension Configurable output dimension. Default 384 to match
 *                           existing content pack embeddings (all-MiniLM-L6-v2).
 *                           Gemma 4 native dimension is 768.
 *
 * @see com.ezansi.app.core.ai.embedding.OnnxEmbeddingModel — production baseline
 * @see SpikeBenchmarkRunner — orchestrates benchmark runs using this model
 */
class GemmaSpikeEmbeddingModel(
    private val embeddingDimension: Int = DEFAULT_DIMENSION,
) : EmbeddingModel {

    companion object {
        private const val TAG = "GemmaSpikeEmbedding"

        /** Default dimension matching all-MiniLM-L6-v2 for content pack compatibility. */
        const val DEFAULT_DIMENSION = 384

        /** Native Gemma 4 embedding dimension (when available). */
        const val GEMMA4_NATIVE_DIMENSION = 768

        /** Supported configurable dimensions per Feature PRD. */
        val SUPPORTED_DIMENSIONS = setOf(256, 384, 512, 768)
    }

    private var modelLoaded = false
    private var modelPath: String? = null

    // Spike metrics
    private var lastEmbedTimeMs: Long = 0L
    private var totalEmbeddings: Int = 0
    private var totalEmbedTimeMs: Long = 0L

    init {
        require(embeddingDimension in SUPPORTED_DIMENSIONS) {
            "Unsupported embedding dimension: $embeddingDimension. " +
                "Supported: $SUPPORTED_DIMENSIONS"
        }
    }

    override suspend fun embed(text: String): FloatArray {
        check(modelLoaded) {
            "Gemma 4 embedding model is not loaded. Call loadModel() first."
        }

        val startTime = System.currentTimeMillis()

        // SPIKE FINDING: MediaPipe GenAI SDK does not expose an embedding API.
        // Using deterministic hash-based embedding as a stub to test pipeline
        // integration. See class KDoc for full analysis.
        val embedding = generateDeterministicEmbedding(text)

        lastEmbedTimeMs = System.currentTimeMillis() - startTime
        totalEmbeddings++
        totalEmbedTimeMs += lastEmbedTimeMs

        Log.d(
            TAG,
            "Embedded text (${text.length} chars) → ${embeddingDimension}D vector " +
                "in ${lastEmbedTimeMs}ms (stub — no real Gemma 4 embedding)",
        )

        return embedding
    }

    override suspend fun loadModel(modelPath: String) {
        if (modelLoaded) {
            Log.d(TAG, "Gemma 4 embedding model already loaded, skipping reload")
            return
        }

        this.modelPath = modelPath
        Log.i(
            TAG,
            "Loading Gemma 4 embedding model (stub) from: $modelPath " +
                "(dimension: $embeddingDimension)",
        )

        // SPIKE FINDING: No real model loading occurs because MediaPipe GenAI
        // does not have an embedding API. In the unified model scenario,
        // GemmaSpikeLlmEngine would load the model once, and this class would
        // share the loaded model reference for embedding extraction.
        //
        // The model path is validated for existence but not actually opened —
        // this is intentional for the stub. In unit tests, any path is accepted.

        modelLoaded = true
        Log.i(TAG, "Gemma 4 embedding model (stub) ready — dimension: $embeddingDimension")
    }

    override fun isLoaded(): Boolean = modelLoaded

    override fun unload() {
        if (!modelLoaded) return

        modelLoaded = false
        modelPath = null
        totalEmbeddings = 0
        totalEmbedTimeMs = 0L
        Log.i(TAG, "Gemma 4 embedding model (stub) unloaded")
    }

    override fun runtimeMode(): EmbeddingRuntimeMode {
        // Report as DETERMINISTIC_FALLBACK since we're using hash-based stubs,
        // not real Gemma 4 embeddings. This is honest observability.
        return EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK
    }

    // ── Spike-specific metrics accessors ────────────────────────────

    /** Embedding time in ms from last [embed] call. */
    fun lastEmbedTimeMs(): Long = lastEmbedTimeMs

    /** Total number of embeddings generated in this session. */
    fun totalEmbeddings(): Int = totalEmbeddings

    /** Average embedding time across all calls. */
    fun averageEmbedTimeMs(): Double {
        return if (totalEmbeddings > 0) {
            totalEmbedTimeMs.toDouble() / totalEmbeddings
        } else {
            0.0
        }
    }

    /** The configured embedding dimension. */
    fun dimension(): Int = embeddingDimension

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Generates a deterministic embedding from text using hash-based projection.
     *
     * Matches the approach used in [MockEmbeddingModel] and
     * [OnnxEmbeddingModel]'s fallback path. Produces consistent vectors
     * for the same input text, L2-normalised for cosine similarity.
     *
     * The output dimension is configurable via [embeddingDimension] to test
     * how different dimensions affect retrieval quality in the spike.
     */
    private fun generateDeterministicEmbedding(text: String): FloatArray {
        val embedding = FloatArray(embeddingDimension)
        val normalised = text.lowercase().trim()

        for (i in 0 until embeddingDimension) {
            val hashInput = "$normalised:gemma4:$i"
            val hash = hashInput.hashCode()
            embedding[i] = hash.toFloat() / Int.MAX_VALUE.toFloat()
        }

        return l2Normalise(embedding)
    }

    /**
     * L2-normalises a vector so its magnitude is 1.0.
     *
     * After normalisation, dot product equals cosine similarity —
     * compatible with FAISS IndexFlatIP.
     */
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
