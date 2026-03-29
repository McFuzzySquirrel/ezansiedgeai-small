package com.ezansi.app.core.ai.inference

import android.util.Log
import com.ezansi.app.core.llama.LlamaAndroid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * llama.cpp-based LLM engine for on-device Qwen2.5-1.5B inference.
 *
 * Uses the [LlamaAndroid] JNI wrapper to run the Qwen2.5-1.5B-Instruct
 * Q4_K_M GGUF model on-device via CPU (ARM NEON on real devices,
 * x86_64 on emulator).
 *
 * ## Model Details
 *
 * - Model: Qwen2.5-1.5B-Instruct
 * - Quantisation: Q4_K_M (1,066 MB on disk)
 * - Context window: 2048 tokens
 * - Inference: CPU-only with ARM NEON acceleration
 * - Temperature: 0.3 (low randomness for factual explanations)
 * - mmap: enabled for memory-efficient loading
 *
 * ## Inference Flow
 *
 * 1. Load GGUF model via `llama_model_load_from_file()` with mmap
 * 2. Create context with 2048-token window
 * 3. Tokenize prompt → batch decode (prompt eval)
 * 4. Generation loop: sample → detokenize → emit → decode single
 * 5. Stop on EOG token, max_tokens, or 30-second timeout
 *
 * @see MockLlmEngine for development/testing without native libraries
 */
class LlamaCppEngine : LlmEngine {

    companion object {
        private const val TAG = "LlamaCppEngine"

        /** Qwen2.5 context window size in tokens. */
        private const val CONTEXT_SIZE = 2048

        /** CPU threads for inference (single-token generation). */
        private const val N_THREADS = 4

        /** Low temperature for factual, grounded explanations. */
        private const val TEMPERATURE = 0.3f

        /** Top-p (nucleus) sampling threshold. */
        private const val TOP_P = 0.9f

        /** Top-k sampling limit. */
        private const val TOP_K = 40

        /** Wall-time cap for the generation phase (AI-09). */
        private const val GENERATION_TIMEOUT_MS = 90_000L
    }

    private val llama = LlamaAndroid()
    private var modelHandle: Long = 0L
    private var contextHandle: Long = 0L
    private var samplerHandle: Long = 0L
    private var modelLoaded = false
    private var nativeLibAvailable = false

    init {
        nativeLibAvailable = LlamaAndroid.isAvailable()
        if (!nativeLibAvailable) {
            Log.w(
                TAG,
                "llama-jni native library not available. " +
                    "LLM inference disabled — using MockLlmEngine for development.",
            )
        }
    }

    override suspend fun loadModel(modelPath: String) {
        if (modelLoaded) {
            Log.d(TAG, "LLM model already loaded, skipping reload")
            return
        }

        Log.i(TAG, "Loading LLM model from: $modelPath")

        if (!nativeLibAvailable) {
            Log.w(TAG, "Native library not available — model load is a no-op")
            modelLoaded = true
            return
        }

        try {
            modelHandle = llama.nativeLoadModel(modelPath, CONTEXT_SIZE, true)
            if (modelHandle == 0L) {
                throw IllegalStateException("nativeLoadModel returned null handle")
            }

            contextHandle = llama.nativeCreateContext(modelHandle, CONTEXT_SIZE, N_THREADS)
            if (contextHandle == 0L) {
                llama.nativeFreeModel(modelHandle)
                modelHandle = 0L
                throw IllegalStateException("nativeCreateContext returned null handle")
            }

            samplerHandle = llama.nativeCreateSampler(TEMPERATURE, TOP_P, TOP_K)
            if (samplerHandle == 0L) {
                llama.nativeFreeContext(contextHandle)
                llama.nativeFreeModel(modelHandle)
                contextHandle = 0L
                modelHandle = 0L
                throw IllegalStateException("nativeCreateSampler returned null handle")
            }

            modelLoaded = true
            Log.i(TAG, "LLM model loaded and context created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM model", e)
            throw IllegalStateException(
                "Failed to load LLM model from: $modelPath",
                e,
            )
        }
    }

    override fun isLoaded(): Boolean = modelLoaded

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        check(modelLoaded) {
            "LLM model is not loaded. Call loadModel() before generate()."
        }

        if (!nativeLibAvailable) {
            Log.w(TAG, "Llama native unavailable path active during generation")
            emit(
                "The LLM model requires llama.cpp native bindings to generate " +
                    "real explanations. Please integrate the llama-android " +
                    "dependency to enable on-device inference.",
            )
            return@flow
        }

        val startTimeMs = System.currentTimeMillis()

        // Tokenize prompt
        val tokens = llama.nativeTokenize(modelHandle, prompt, true)
        if (tokens == null || tokens.isEmpty()) {
            Log.e(TAG, "Tokenization failed or produced empty result")
            emit("[Error: Failed to tokenize prompt]")
            return@flow
        }
        Log.i(TAG, "Prompt tokenized: ${tokens.size} tokens")

        // Prompt evaluation (batch decode)
        val decodeResult = llama.nativeDecode(contextHandle, tokens)
        if (decodeResult != 0) {
            Log.e(TAG, "Prompt decode failed: $decodeResult")
            emit("[Error: Prompt evaluation failed]")
            return@flow
        }

        val promptTimeMs = System.currentTimeMillis() - startTimeMs
        Log.i(TAG, "Prompt eval completed in ${promptTimeMs}ms")

        // Generation loop: sample → detokenize → emit → decode single
        val generationStartMs = System.currentTimeMillis()
        var tokensGenerated = 0

        while (tokensGenerated < maxTokens) {
            // Wall-time cap (AI-09)
            val elapsed = System.currentTimeMillis() - generationStartMs
            if (elapsed > GENERATION_TIMEOUT_MS) {
                Log.w(TAG, "Generation timeout after ${elapsed}ms ($tokensGenerated tokens)")
                break
            }

            // Sample next token
            val tokenId = llama.nativeSample(samplerHandle, contextHandle)

            // Check end-of-generation
            if (llama.nativeIsEog(modelHandle, tokenId)) {
                Log.i(TAG, "EOG token reached after $tokensGenerated tokens")
                break
            }

            // Detokenize and emit
            val tokenText = llama.nativeDetokenize(modelHandle, tokenId)
            emit(tokenText)
            tokensGenerated++

            // Feed the sampled token back for next decode
            val singleResult = llama.nativeDecodeSingle(contextHandle, tokenId)
            if (singleResult != 0) {
                Log.e(TAG, "Single-token decode failed: $singleResult")
                break
            }
        }

        val totalMs = System.currentTimeMillis() - startTimeMs
        val genMs = System.currentTimeMillis() - generationStartMs
        val tokPerSec = if (genMs > 0) tokensGenerated * 1000.0 / genMs else 0.0
        Log.i(
            TAG,
            "Generation complete: $tokensGenerated tokens in ${totalMs}ms " +
                "(prompt=${promptTimeMs}ms, gen=${genMs}ms, %.1f tok/s)".format(tokPerSec),
        )
    }

    override fun unload() {
        if (!modelLoaded) return

        try {
            if (nativeLibAvailable) {
                if (samplerHandle != 0L) {
                    llama.nativeFreeSampler(samplerHandle)
                    samplerHandle = 0L
                }
                if (contextHandle != 0L) {
                    llama.nativeFreeContext(contextHandle)
                    contextHandle = 0L
                }
                if (modelHandle != 0L) {
                    llama.nativeFreeModel(modelHandle)
                    modelHandle = 0L
                }
            }
            modelLoaded = false
            Log.i(TAG, "LLM model unloaded — RAM freed")
        } catch (e: Exception) {
            Log.w(TAG, "Error during LLM model unload", e)
            modelLoaded = false
        }
    }

    override fun runtimeMode(): LlmRuntimeMode {
        return if (nativeLibAvailable) {
            LlmRuntimeMode.REAL_NATIVE
        } else {
            LlmRuntimeMode.NATIVE_UNAVAILABLE
        }
    }
}
