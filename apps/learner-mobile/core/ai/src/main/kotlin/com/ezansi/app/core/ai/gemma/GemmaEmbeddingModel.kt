package com.ezansi.app.core.ai.gemma

import android.util.Log
import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import kotlin.math.sqrt

/**
 * Gemma 4 embedding model that uses the unified [GemmaModelProvider] to produce
 * embedding vectors for the RAG retrieval pipeline.
 *
 * ## Shared Model Architecture
 *
 * This class does **not** own the model lifecycle. The [GemmaModelProvider] is the
 * single lifecycle owner — it loads and holds the MediaPipe `LlmInference` handle
 * that is shared between this embedding model and the generation engine
 * (`GemmaLiteRtEngine`). This unified approach keeps peak RAM ≤1,200 MB (FT-NF-04)
 * by avoiding the sequential load/unload pattern of the legacy pipeline.
 *
 * ## Current Embedding Strategy
 *
 * **As of MediaPipe GenAI SDK 0.10.x, there is no dedicated embedding extraction
 * API.** The `LlmInference` class exposes only text generation, not hidden-state
 * or pooled-embedding access. Until MediaPipe adds an embedding API (or a separate
 * Gemma 4 embedding `.tflite` is available), this class uses a **deterministic
 * hash-based embedding** that:
 * - Produces consistent vectors for the same input text
 * - Supports configurable dimensions (256 / 384 / 512 / 768)
 * - Returns L2-normalised vectors compatible with FAISS IndexFlatIP
 *
 * The runtime mode honestly reports [EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK]
 * while this stub path is active, regardless of whether the underlying MediaPipe
 * model is loaded.
 *
 * ## Embedding Dimension Contract
 *
 * The output dimension is read from the provider's active config at embed-time,
 * falling back to the constructor [config]'s `embeddingDimension`. This must
 * match the dimension used when building the content pack's FAISS index —
 * coordinated with the content-pack-engineer (FT-FR-05).
 *
 * @param modelProvider Shared Gemma 4 lifecycle manager. Injected from `AppContainer`.
 * @param config Default configuration. The [GemmaModelConfig.embeddingDimension]
 *               is used as a fallback when the provider has no active config.
 *
 * @see GemmaModelProvider
 * @see GemmaModelConfig
 * @see EmbeddingModel
 */
class GemmaEmbeddingModel(
    private val modelProvider: GemmaModelProvider,
    private val config: GemmaModelConfig = GemmaModelConfig(modelPath = "default"),
) : EmbeddingModel {

    companion object {
        private const val TAG = "GemmaEmbeddingModel"
    }

    // ── Public API (EmbeddingModel) ─────────────────────────────────

    /**
     * Loads the Gemma 4 model via the shared [GemmaModelProvider].
     *
     * Creates a [GemmaModelConfig] using the provided [modelPath] and defaults
     * from the constructor [config] (preserving `embeddingDimension`, GPU
     * settings, and inference parameters). The provider handles idempotent
     * loading — if the same model is already loaded, this is a no-op.
     *
     * @param modelPath Absolute path to the Gemma 4 `.task` model file.
     * @throws java.io.FileNotFoundException if the model file does not exist.
     * @throws IllegalStateException if the model cannot be loaded.
     */
    override suspend fun loadModel(modelPath: String) {
        Log.i(TAG, "Loading Gemma 4 model for embedding from: $modelPath")

        val loadConfig = config.copy(modelPath = modelPath)
        modelProvider.loadModel(loadConfig)

        Log.i(
            TAG,
            "Gemma 4 model ready for embedding — dimension: ${embeddingDimension()}, " +
                "runtime: ${modelProvider.runtimeMode()}",
        )
    }

    /**
     * Returns true if the embedding model is ready for use.
     *
     * Since the current implementation uses deterministic hash-based embedding
     * (MediaPipe GenAI SDK lacks an embedding extraction API), this always
     * returns `true` — the hash-based path requires no model file.
     *
     * When real Gemma 4 embedding is implemented, this should delegate to
     * [GemmaModelProvider.isLoaded] to gate on actual model availability.
     */
    override fun isLoaded(): Boolean = true

    /**
     * Converts input text into an L2-normalised embedding vector.
     *
     * The output dimension is determined by the provider's active config
     * (or the constructor config as fallback). The vector is suitable for
     * cosine similarity comparison via FAISS IndexFlatIP.
     *
     * **Current implementation:** Deterministic hash-based embedding (no real
     * Gemma 4 embedding extraction available via MediaPipe GenAI SDK).
     *
     * @param text The learner's question or search query.
     * @return An L2-normalised embedding vector of the configured dimension.
     * @throws IllegalStateException if the model has not been loaded.
     *
     * **Note:** The current hash-based implementation does not actually require
     * the model to be loaded, so this method will not throw.
     */
    override suspend fun embed(text: String): FloatArray {
        if (modelProvider.isLoaded()) {
            modelProvider.setMode(GemmaModelProvider.ModelMode.EMBEDDING)
        }

        val dimension = embeddingDimension()
        val inference = if (modelProvider.isLoaded()) modelProvider.getInference() else null

        // TODO(ai-pipeline-engineer): When MediaPipe GenAI SDK adds an embedding
        //  extraction API (hidden-state pooling or a dedicated embedding mode),
        //  implement real Gemma 4 embedding here using the `inference` handle.
        //  Until then, both the real-model and stub paths use deterministic
        //  hash-based embedding. See spike P0-006 findings.
        val embedding = if (inference != null) {
            // MediaPipe model is loaded but no embedding API is available yet.
            // Fall back to deterministic embedding — same output as stub mode.
            Log.d(
                TAG,
                "MediaPipe model loaded but no embedding API available — " +
                    "using deterministic hash-based embedding (dim=$dimension)",
            )
            hashBasedEmbedding(text, dimension)
        } else {
            // Stub mode — no MediaPipe on classpath (JVM unit tests)
            Log.d(TAG, "Stub mode — using deterministic hash-based embedding (dim=$dimension)")
            hashBasedEmbedding(text, dimension)
        }

        return embedding
    }

    /**
     * Releases local state without unloading the shared model.
     *
     * Since [GemmaModelProvider] is the lifecycle owner and is shared with
     * the generation engine, this method does **not** call
     * [GemmaModelProvider.unloadModel]. It only sets the mode to
     * [GemmaModelProvider.ModelMode.IDLE] to signal that embedding is no
     * longer active.
     *
     * The actual model unload is the responsibility of whoever manages
     * the provider lifecycle (typically `AppContainer` or the orchestrator).
     */
    override fun unload() {
        Log.i(TAG, "Releasing embedding state (shared model NOT unloaded)")
        modelProvider.setMode(GemmaModelProvider.ModelMode.IDLE)
    }

    /**
     * Returns the effective runtime mode for embedding operations.
     *
     * Since the current implementation always uses deterministic hash-based
     * embedding (MediaPipe GenAI SDK lacks an embedding extraction API),
     * this returns [EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK] when the
     * provider is in real or unavailable mode, and [EmbeddingRuntimeMode.MOCK]
     * when in stub mode (JVM unit tests).
     *
     * TODO(ai-pipeline-engineer): Return [EmbeddingRuntimeMode.REAL_MEDIAPIPE]
     *  once actual Gemma 4 embedding extraction is implemented.
     */
    override fun runtimeMode(): EmbeddingRuntimeMode {
        return when (modelProvider.runtimeMode()) {
            GemmaRuntimeMode.REAL_MEDIAPIPE -> {
                // Even with MediaPipe loaded, we use hash-based embedding
                // because the SDK doesn't expose an embedding API yet.
                EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK
            }
            GemmaRuntimeMode.MEDIAPIPE_UNAVAILABLE -> {
                EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK
            }
            GemmaRuntimeMode.STUB -> {
                EmbeddingRuntimeMode.MOCK
            }
        }
    }

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * Returns the active embedding dimension from the provider's config,
     * falling back to the constructor config's dimension.
     */
    internal fun embeddingDimension(): Int {
        return modelProvider.activeConfig()?.embeddingDimension
            ?: config.embeddingDimension
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Generates a deterministic embedding from text using hash-based projection.
     *
     * This is **not** a real semantic embedding — it produces consistent vectors
     * for the same input text, which allows the retrieval pipeline to function
     * end-to-end while real Gemma 4 embedding support is pending.
     *
     * Uses the text's [hashCode] as a seed for a [java.util.Random] instance,
     * ensuring identical output for identical input across runs. The raw
     * values are drawn from [-1, 1] and then L2-normalised.
     *
     * @param text Input text to embed.
     * @param dimension Output vector dimension.
     * @return An L2-normalised [FloatArray] of the specified dimension.
     */
    private fun hashBasedEmbedding(text: String, dimension: Int): FloatArray {
        val seed = text.hashCode().toLong()
        val random = java.util.Random(seed)
        val raw = FloatArray(dimension) { random.nextFloat() * 2f - 1f }
        return l2Normalise(raw)
    }

    /**
     * L2-normalises a vector so its magnitude is 1.0.
     *
     * After normalisation, the dot product of two vectors equals their
     * cosine similarity — compatible with FAISS IndexFlatIP.
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
