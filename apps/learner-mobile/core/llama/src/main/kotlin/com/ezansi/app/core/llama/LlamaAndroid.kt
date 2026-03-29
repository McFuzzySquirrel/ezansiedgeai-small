package com.ezansi.app.core.llama

import android.util.Log

/**
 * Kotlin wrapper for llama.cpp native JNI functions.
 *
 * Provides low-level access to llama.cpp for GGUF model loading,
 * tokenization, decode, sampling, and detokenization. Used by
 * [com.ezansi.app.core.ai.inference.LlamaCppEngine] to run
 * Qwen2.5-1.5B-Instruct inference on-device.
 */
class LlamaAndroid {

    companion object {
        private const val TAG = "LlamaAndroid"

        /** Attempt to load the native library. Returns true if available. */
        fun isAvailable(): Boolean {
            return try {
                System.loadLibrary("llama-jni")
                Log.i(TAG, "llama-jni native library loaded")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "llama-jni native library not available: ${e.message}")
                false
            }
        }
    }

    // ── Model Loading ────────────────────────────────────────────

    /**
     * Load a GGUF model from the given file path.
     * @param path Absolute path to the .gguf file
     * @param contextSize Maximum context window (tokens)
     * @param useMmap Use memory-mapped I/O for efficient loading
     * @return Native model handle (0 if failed)
     */
    external fun nativeLoadModel(path: String, contextSize: Int, useMmap: Boolean): Long

    /**
     * Create an inference context from a loaded model.
     * @param modelHandle Handle from [nativeLoadModel]
     * @param contextSize Context window size
     * @param nThreads Number of CPU threads for inference
     * @return Native context handle (0 if failed)
     */
    external fun nativeCreateContext(modelHandle: Long, contextSize: Int, nThreads: Int): Long

    // ── Tokenization ─────────────────────────────────────────────

    /**
     * Tokenize text into token IDs.
     * @param modelHandle Handle from [nativeLoadModel]
     * @param text Text to tokenize
     * @param addSpecial Add BOS/EOS special tokens
     * @return Array of token IDs, or null on failure
     */
    external fun nativeTokenize(modelHandle: Long, text: String, addSpecial: Boolean): IntArray?

    // ── Decode ───────────────────────────────────────────────────

    /**
     * Decode a batch of tokens (prompt evaluation).
     * @param contextHandle Handle from [nativeCreateContext]
     * @param tokens Array of token IDs to evaluate
     * @return 0 on success, negative on error
     */
    external fun nativeDecode(contextHandle: Long, tokens: IntArray): Int

    /**
     * Decode a single token (used during generation loop).
     * @param contextHandle Handle from [nativeCreateContext]
     * @param tokenId Single token to evaluate
     * @return 0 on success, negative on error
     */
    external fun nativeDecodeSingle(contextHandle: Long, tokenId: Int): Int

    // ── Sampling ─────────────────────────────────────────────────

    /**
     * Create a sampler chain with temperature, top-p, and top-k.
     * @return Native sampler handle
     */
    external fun nativeCreateSampler(temperature: Float, topP: Float, topK: Int): Long

    /**
     * Sample the next token from the context logits.
     * @param samplerHandle Handle from [nativeCreateSampler]
     * @param contextHandle Handle from [nativeCreateContext]
     * @return Sampled token ID
     */
    external fun nativeSample(samplerHandle: Long, contextHandle: Long): Int

    /** Free a sampler created with [nativeCreateSampler]. */
    external fun nativeFreeSampler(samplerHandle: Long)

    // ── Detokenization ───────────────────────────────────────────

    /**
     * Convert a token ID back to text.
     * @param modelHandle Handle from [nativeLoadModel]
     * @param tokenId Token to convert
     * @return Text piece for the token
     */
    external fun nativeDetokenize(modelHandle: Long, tokenId: Int): String

    // ── End-of-generation ────────────────────────────────────────

    /**
     * Check if a token is an end-of-generation token (EOS, EOT, etc.).
     * @param modelHandle Handle from [nativeLoadModel]
     * @param tokenId Token to check
     * @return true if the token signals generation should stop
     */
    external fun nativeIsEog(modelHandle: Long, tokenId: Int): Boolean

    // ── Cleanup ──────────────────────────────────────────────────

    /** Free inference context. */
    external fun nativeFreeContext(contextHandle: Long)

    /** Free model. */
    external fun nativeFreeModel(modelHandle: Long)

    /** Free the llama.cpp backend (call at app shutdown). */
    external fun nativeBackendFree()
}
