package com.ezansi.app.core.ai.embedding

/**
 * On-device embedding model for converting text into vector representations.
 *
 * The embedding model converts learner questions into 384-dimensional vectors
 * that can be compared against pre-computed content chunk embeddings via
 * cosine similarity. This is the first step in the RAG pipeline:
 * question → embed → retrieve → prompt → generate.
 *
 * Implementors must ensure:
 * - Model loading is lazy (on first [embed] call, not at app startup)
 * - Model can be [unload]ed to free RAM before the LLM loads (AI-08)
 * - All inference runs on-device with no network calls (SP-02)
 * - Output vectors are L2-normalised for cosine similarity compatibility
 *
 * Production: [OnnxEmbeddingModel] (all-MiniLM-L6-v2 via ONNX Runtime)
 * Development: [MockEmbeddingModel] (deterministic random vectors)
 *
 * @see com.ezansi.app.core.ai.ExplanationEngine
 */
interface EmbeddingModel {

    /**
     * Converts input text into a normalised embedding vector.
     *
     * The returned [FloatArray] has 384 dimensions (all-MiniLM-L6-v2)
     * and is L2-normalised so that dot product equals cosine similarity.
     *
     * @param text The learner's question or search query.
     * @return A 384-dimensional L2-normalised embedding vector.
     * @throws IllegalStateException if the model has not been loaded.
     */
    suspend fun embed(text: String): FloatArray

    /**
     * Loads the embedding model from the given file path into memory.
     *
     * This is an expensive operation (~1 second, ~87 MB RAM) and should
     * only be called lazily on first query. The model should be unloaded
     * before loading the LLM to stay within the 2 GB RAM budget.
     *
     * @param modelPath Absolute path to the ONNX model file on local storage.
     * @throws java.io.FileNotFoundException if the model file does not exist.
     */
    suspend fun loadModel(modelPath: String)

    /**
     * Returns true if the model is currently loaded and ready for inference.
     */
    fun isLoaded(): Boolean

    /**
     * Releases the model from memory to free RAM for the LLM (AI-08).
     *
     * After calling [unload], [isLoaded] returns false and subsequent
     * [embed] calls will throw until [loadModel] is called again.
     * This is safe to call even if the model is not loaded.
     */
    fun unload()

    /**
     * Runtime mode for observability during emulator/device validation.
     */
    fun runtimeMode(): EmbeddingRuntimeMode = EmbeddingRuntimeMode.UNKNOWN
}

/**
 * Observable runtime mode for embedding inference.
 */
enum class EmbeddingRuntimeMode {
    REAL_ONNX,
    DETERMINISTIC_FALLBACK,
    MOCK,
    UNKNOWN,
}
