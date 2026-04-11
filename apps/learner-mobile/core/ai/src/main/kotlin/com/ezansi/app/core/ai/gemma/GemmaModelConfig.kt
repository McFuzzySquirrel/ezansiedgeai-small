package com.ezansi.app.core.ai.gemma

/**
 * Configuration for the unified Gemma 4 model loaded via [GemmaModelProvider].
 *
 * This config is shared between the LLM engine (generation) and the embedding
 * model (vector extraction), since both use the same underlying MediaPipe
 * `LlmInference` instance. The [GemmaModelProvider] passes relevant fields to
 * the MediaPipe builder at model-load time.
 *
 * ## Defaults
 *
 * Defaults match the project's inference requirements:
 * - Low temperature (0.3) for factual, grounded explanations (PRD §5.4)
 * - GPU delegation enabled with CPU fallback for broadest device support
 * - 768-dim embeddings (Gemma 4 native), configurable down to 256 for
 *   content packs that use smaller dimensions
 *
 * ## Context Window Budget (PT-04)
 *
 * The [maxTokens] value of 512 covers the generation budget. The full 2048-token
 * context window is managed by [GemmaModelProvider] when building the MediaPipe
 * options (see [GemmaModelProvider.CONTEXT_SIZE]).
 *
 * @param modelPath       Absolute path to the Gemma 4 `.task` model file on local storage.
 * @param maxTokens       Maximum tokens for generation output (default: 512).
 * @param temperature     Sampling temperature. Lower = more deterministic (default: 0.3).
 * @param topK            Top-k sampling limit (default: 40).
 * @param randomSeed      Fixed seed for reproducible output (default: 42).
 * @param useGpuDelegate  Whether to attempt GPU delegation via LiteRT (default: true).
 * @param gpuFallbackToCpu If true, fall back to CPU when GPU delegate fails (default: true).
 * @param embeddingDimension Output embedding dimension. Must be in [SUPPORTED_DIMENSIONS].
 *
 * @see GemmaModelProvider
 */
data class GemmaModelConfig(
    val modelPath: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val randomSeed: Int = DEFAULT_RANDOM_SEED,
    val useGpuDelegate: Boolean = true,
    val gpuFallbackToCpu: Boolean = true,
    val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION,
) {
    init {
        require(modelPath.isNotBlank()) {
            "modelPath must not be blank"
        }
        require(maxTokens > 0) {
            "maxTokens must be positive, got $maxTokens"
        }
        require(temperature in 0.0f..2.0f) {
            "temperature must be in [0.0, 2.0], got $temperature"
        }
        require(topK > 0) {
            "topK must be positive, got $topK"
        }
        require(embeddingDimension in SUPPORTED_DIMENSIONS) {
            "embeddingDimension must be one of $SUPPORTED_DIMENSIONS, got $embeddingDimension"
        }
    }

    companion object {
        /** Default maximum generation tokens. */
        const val DEFAULT_MAX_TOKENS = 512

        /** Low temperature for factual explanations (PRD §5.4). */
        const val DEFAULT_TEMPERATURE = 0.3f

        /** Default top-k sampling limit. */
        const val DEFAULT_TOP_K = 40

        /** Fixed seed for reproducible inference (spike benchmarking). */
        const val DEFAULT_RANDOM_SEED = 42

        /** Gemma 4 native embedding dimension. */
        const val DEFAULT_EMBEDDING_DIMENSION = 768

        /** Supported configurable dimensions per Feature PRD §6 FT-FR-05. */
        val SUPPORTED_DIMENSIONS = setOf(256, 384, 512, 768)
    }
}
