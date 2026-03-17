package com.ezansi.app.core.ai.inference

import kotlinx.coroutines.flow.Flow

/**
 * On-device LLM inference engine for generating explanations.
 *
 * The LLM engine is the final step in the RAG pipeline:
 * question → embed → retrieve → prompt → **generate**.
 *
 * It loads a quantised language model and generates text token-by-token,
 * streaming output via a [Flow] so the UI can show progressive responses.
 *
 * Implementors must ensure:
 * - Model loading is lazy (on first generate call, not at app startup)
 * - Generation respects the 30-second wall-time cap (AI-09)
 * - Total generation does not exceed max_tokens parameter (AI-04)
 * - Model can be [unload]ed to free RAM (AI-08)
 * - All inference runs on-device with no network calls (SP-02)
 *
 * Production: [LlamaCppEngine] (Qwen2.5-1.5B Q4_K_M via llama.cpp)
 * Development: [MockLlmEngine] (simulated token-by-token output)
 *
 * @see com.ezansi.app.core.ai.ExplanationEngine
 */
interface LlmEngine {

    /**
     * Loads the LLM from the given file path into memory.
     *
     * This is an expensive operation (~1 second, ~1066 MB RAM for Q4_K_M)
     * and should only be called after the embedding model has been unloaded
     * to stay within the 2 GB RAM budget (AI-08).
     *
     * @param modelPath Absolute path to the GGUF model file on local storage.
     * @throws java.io.FileNotFoundException if the model file does not exist.
     * @throws IllegalStateException if the model cannot be loaded.
     */
    suspend fun loadModel(modelPath: String)

    /**
     * Returns true if the LLM is currently loaded and ready for inference.
     */
    fun isLoaded(): Boolean

    /**
     * Generates text token-by-token from the given prompt.
     *
     * Returns a [Flow] that emits individual tokens (or small token groups)
     * as they are generated. The flow completes when:
     * - The model emits an end-of-sequence token
     * - [maxTokens] tokens have been generated
     * - The 30-second wall-time cap is reached (AI-09)
     *
     * @param prompt The complete formatted prompt (including chat template).
     * @param maxTokens Maximum number of tokens to generate (default: 512).
     * @return A [Flow] of token strings. Collect to get the full response.
     */
    fun generate(prompt: String, maxTokens: Int = 512): Flow<String>

    /**
     * Releases the model from memory.
     *
     * After calling [unload], [isLoaded] returns false and subsequent
     * [generate] calls will fail until [loadModel] is called again.
     * Safe to call even if the model is not loaded.
     */
    fun unload()
}
