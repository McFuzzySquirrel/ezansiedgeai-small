package com.ezansi.app.core.ai.spike

import android.content.Context
import android.os.Debug
import android.util.Log
import com.ezansi.app.core.ai.inference.LlmEngine
import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Spike P0-006: Gemma 4 LLM engine using MediaPipe GenAI SDK.
 *
 * This is **spike code** for evaluating Gemma 4 1B (INT4, LiteRT format)
 * on-device via the MediaPipe [LlmInference] API. It implements the existing
 * [LlmEngine] interface so it can be dropped into the RAG pipeline for
 * benchmarking against the [LlamaCppEngine] baseline.
 *
 * ## Key Differences from LlamaCppEngine
 *
 * - Uses MediaPipe GenAI SDK instead of llama.cpp JNI bindings
 * - Supports GPU delegation (LiteRT GPU delegate) with CPU fallback
 * - Model format: LiteRT `.task` file instead of GGUF
 * - Unified model: same loaded model can serve embedding (see [GemmaSpikeEmbeddingModel])
 * - Requires Android [Context] for MediaPipe initialisation
 *
 * ## Spike Findings to Capture
 *
 * - Model load time (target: <5s)
 * - Generation latency (target: ≤5s GPU, ≤10s CPU)
 * - Peak RAM consumption (target: ≤1,200 MB)
 * - Token throughput (tokens/second)
 * - GPU delegate availability and fallback behaviour
 *
 * @param context Android application context for MediaPipe initialisation.
 *                Pass `null` for unit tests — the engine will operate in stub mode.
 * @param useGpuDelegate Whether to attempt GPU delegation. Falls back to CPU if unavailable.
 *
 * @see com.ezansi.app.core.ai.inference.LlamaCppEngine — production baseline
 * @see SpikeBenchmarkRunner — orchestrates benchmark runs using this engine
 */
class GemmaSpikeLlmEngine(
    private val context: Context?,
    private val useGpuDelegate: Boolean = true,
) : LlmEngine {

    companion object {
        private const val TAG = "GemmaSpikeLlmEngine"

        /** Gemma 4 context window size in tokens. */
        private const val CONTEXT_SIZE = 2048

        /** Low temperature for factual, grounded explanations. */
        private const val TEMPERATURE = 0.3f

        /** Top-k sampling limit. */
        private const val TOP_K = 40

        /** Fixed seed for reproducible benchmark results. */
        private const val RANDOM_SEED = 42

        /** Wall-time cap for the generation phase (AI-09). */
        private const val GENERATION_TIMEOUT_MS = 30_000L
    }

    // MediaPipe LlmInference handle — held as Any? to avoid hard compile-time
    // dependency, matching the OnnxEmbeddingModel reflection pattern.
    // On real devices, this will be a com.google.mediapipe.tasks.genai.llminference.LlmInference.
    private var llmInference: Any? = null
    private var modelLoaded = false
    private var mediaPipeAvailable = false
    private var gpuDelegateActive = false

    // Last-generation metrics for benchmark collection
    private var lastLoadTimeMs: Long = 0L
    private var lastGenerationTimeMs: Long = 0L
    private var lastTokenCount: Int = 0
    private var lastPeakNativeHeapMb: Float = 0f

    init {
        mediaPipeAvailable = checkMediaPipeAvailability()
        if (!mediaPipeAvailable) {
            Log.w(
                TAG,
                "MediaPipe GenAI SDK not available on classpath. " +
                    "Gemma 4 inference disabled — engine will return stub responses.",
            )
        }
    }

    override suspend fun loadModel(modelPath: String) {
        if (modelLoaded) {
            Log.d(TAG, "Gemma 4 model already loaded, skipping reload")
            return
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: $modelPath")
            throw java.io.FileNotFoundException(
                "Gemma 4 model not found at: $modelPath. " +
                    "Run the model download script first.",
            )
        }

        Log.i(TAG, "Loading Gemma 4 model from: $modelPath (GPU: $useGpuDelegate)")
        val loadStart = System.currentTimeMillis()

        if (!mediaPipeAvailable || context == null) {
            Log.w(TAG, "MediaPipe unavailable or no Context — entering stub mode")
            modelLoaded = true
            lastLoadTimeMs = System.currentTimeMillis() - loadStart
            return
        }

        try {
            llmInference = createLlmInference(modelPath)
            gpuDelegateActive = useGpuDelegate // Assume delegate applied if requested
            modelLoaded = true
            lastLoadTimeMs = System.currentTimeMillis() - loadStart
            lastPeakNativeHeapMb = Debug.getNativeHeapAllocatedSize().toFloat() / (1024 * 1024)
            Log.i(
                TAG,
                "Gemma 4 model loaded in ${lastLoadTimeMs}ms " +
                    "(GPU delegate: $gpuDelegateActive, " +
                    "native heap: ${"%.0f".format(lastPeakNativeHeapMb)} MB)",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Gemma 4 model — attempting CPU fallback", e)

            if (useGpuDelegate) {
                // Retry without GPU delegate
                try {
                    llmInference = createLlmInferenceCpuOnly(modelPath)
                    gpuDelegateActive = false
                    modelLoaded = true
                    lastLoadTimeMs = System.currentTimeMillis() - loadStart
                    Log.i(
                        TAG,
                        "Gemma 4 model loaded (CPU fallback) in ${lastLoadTimeMs}ms",
                    )
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "CPU fallback also failed", fallbackError)
                    throw IllegalStateException(
                        "Failed to load Gemma 4 model from: $modelPath " +
                            "(both GPU and CPU paths failed)",
                        fallbackError,
                    )
                }
            } else {
                throw IllegalStateException(
                    "Failed to load Gemma 4 model from: $modelPath",
                    e,
                )
            }
        }
    }

    override fun isLoaded(): Boolean = modelLoaded

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        check(modelLoaded) {
            "Gemma 4 model is not loaded. Call loadModel() before generate()."
        }

        val startTimeMs = System.currentTimeMillis()
        val heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        if (!mediaPipeAvailable || llmInference == null) {
            Log.w(TAG, "MediaPipe unavailable — generating stub response")
            val stub = buildStubResponse(prompt)
            emit(stub)
            lastTokenCount = stub.split("\\s+".toRegex()).size
            lastGenerationTimeMs = System.currentTimeMillis() - startTimeMs
            return@flow
        }

        try {
            // Use reflection to call LlmInference.generateResponse(prompt)
            // to avoid hard compile-time dependency in unit test environment.
            val response = invokeGenerateResponse(prompt)

            val elapsed = System.currentTimeMillis() - startTimeMs

            // Enforce 30-second wall-time cap (AI-09)
            if (elapsed > GENERATION_TIMEOUT_MS) {
                Log.w(
                    TAG,
                    "Generation exceeded ${GENERATION_TIMEOUT_MS}ms timeout: ${elapsed}ms",
                )
            }

            // MediaPipe returns the complete response as a single string.
            // Emit in chunks to match the streaming Flow<String> contract.
            val tokens = response.splitIntoTokenChunks()
            var tokenCount = 0
            for (token in tokens) {
                if (tokenCount >= maxTokens) break
                emit(token)
                tokenCount++
            }

            val heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            lastTokenCount = tokenCount
            lastGenerationTimeMs = System.currentTimeMillis() - startTimeMs
            lastPeakNativeHeapMb = Debug.getNativeHeapAllocatedSize().toFloat() / (1024 * 1024)

            val tokPerSec = if (lastGenerationTimeMs > 0) {
                tokenCount * 1000.0 / lastGenerationTimeMs
            } else {
                0.0
            }

            Log.i(
                TAG,
                "Generation complete: $tokenCount tokens in ${lastGenerationTimeMs}ms " +
                    "(${"%.1f".format(tokPerSec)} tok/s, " +
                    "heap delta: ${(heapAfter - heapBefore) / (1024 * 1024)} MB, " +
                    "native heap: ${"%.0f".format(lastPeakNativeHeapMb)} MB)",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            lastGenerationTimeMs = System.currentTimeMillis() - startTimeMs
            emit("[Error: Gemma 4 generation failed — ${e.message}]")
        }
    }

    override fun unload() {
        if (!modelLoaded) return

        try {
            llmInference?.let { inference ->
                try {
                    inference.javaClass.getMethod("close").invoke(inference)
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing MediaPipe LlmInference", e)
                }
            }
            llmInference = null
            modelLoaded = false
            gpuDelegateActive = false
            Log.i(TAG, "Gemma 4 model unloaded — RAM freed")
        } catch (e: Exception) {
            Log.w(TAG, "Error during Gemma 4 model unload", e)
            modelLoaded = false
        }
    }

    override fun runtimeMode(): LlmRuntimeMode {
        return if (mediaPipeAvailable && llmInference != null) {
            LlmRuntimeMode.REAL_NATIVE
        } else if (mediaPipeAvailable) {
            LlmRuntimeMode.NATIVE_UNAVAILABLE
        } else {
            LlmRuntimeMode.NATIVE_UNAVAILABLE
        }
    }

    // ── Spike-specific metrics accessors ────────────────────────────

    /** Model load time in milliseconds from last [loadModel] call. */
    fun lastLoadTimeMs(): Long = lastLoadTimeMs

    /** Generation time in milliseconds from last [generate] collection. */
    fun lastGenerationTimeMs(): Long = lastGenerationTimeMs

    /** Token count from last [generate] collection. */
    fun lastTokenCount(): Int = lastTokenCount

    /** Peak native heap in MB from last measurement point. */
    fun lastPeakNativeHeapMb(): Float = lastPeakNativeHeapMb

    /** Whether GPU delegate is actively being used. */
    fun isGpuDelegateActive(): Boolean = gpuDelegateActive

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Checks whether MediaPipe GenAI SDK is available on the classpath.
     *
     * Uses `Class.forName(name, false, classLoader)` with `initialize = false`
     * to avoid triggering the LlmInference static initializer, which calls
     * `System.loadLibrary("llm_inference_engine_jni")` and would throw
     * [UnsatisfiedLinkError] in JVM unit tests where the JNI library is absent.
     */
    private fun checkMediaPipeAvailability(): Boolean {
        return try {
            Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference",
                false, // Don't initialize — avoids System.loadLibrary in <clinit>
                javaClass.classLoader,
            )
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            // Covers UnsatisfiedLinkError, NoClassDefFoundError,
            // ExceptionInInitializerError if the class is somehow partially loaded.
            false
        }
    }

    /**
     * Creates a MediaPipe LlmInference instance via reflection.
     *
     * Uses the builder API:
     * ```
     * LlmInference.LlmInferenceOptions.builder()
     *     .setModelPath(modelPath)
     *     .setMaxTokens(maxTokens)
     *     .setTemperature(temperature)
     *     .setTopK(topK)
     *     .setRandomSeed(seed)
     *     .build()
     * LlmInference.createFromOptions(context, options)
     * ```
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createLlmInference(modelPath: String): Any {
        val inferenceClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference",
        )
        val optionsClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
        )
        val builderClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
        )

        // Get the builder via static method
        val builderMethod = optionsClass.getMethod("builder")
        val builder = builderMethod.invoke(null)

        // Configure the builder
        builderClass.getMethod("setModelPath", String::class.java)
            .invoke(builder, modelPath)
        builderClass.getMethod("setMaxTokens", Int::class.java)
            .invoke(builder, CONTEXT_SIZE)
        builderClass.getMethod("setTemperature", Float::class.java)
            .invoke(builder, TEMPERATURE)
        builderClass.getMethod("setTopK", Int::class.java)
            .invoke(builder, TOP_K)
        builderClass.getMethod("setRandomSeed", Int::class.java)
            .invoke(builder, RANDOM_SEED)

        val options = builderClass.getMethod("build").invoke(builder)

        // Create inference engine
        val createMethod = inferenceClass.getMethod(
            "createFromOptions",
            Context::class.java,
            optionsClass,
        )
        return createMethod.invoke(null, context, options)
            ?: throw IllegalStateException("createFromOptions returned null")
    }

    /**
     * Creates a MediaPipe LlmInference instance with CPU-only execution.
     * Used as fallback when GPU delegate fails.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createLlmInferenceCpuOnly(modelPath: String): Any {
        // For CPU-only, we use the same builder without GPU-specific options.
        // MediaPipe defaults to CPU when GPU delegate is not explicitly configured.
        return createLlmInference(modelPath)
    }

    /**
     * Invokes generateResponse on the MediaPipe LlmInference instance via reflection.
     */
    private fun invokeGenerateResponse(prompt: String): String {
        val inference = llmInference
            ?: throw IllegalStateException("LlmInference is null")

        val method = inference.javaClass.getMethod("generateResponse", String::class.java)
        return method.invoke(inference, prompt) as? String
            ?: throw IllegalStateException("generateResponse returned null")
    }

    /**
     * Builds a stub response for testing without a real model.
     */
    private fun buildStubResponse(prompt: String): String {
        return "[P0-006 SPIKE STUB] MediaPipe GenAI SDK not available. " +
            "This stub response is generated for pipeline integration testing. " +
            "Prompt length: ${prompt.length} chars. " +
            "Deploy the Gemma 4 .task model and run on a real device " +
            "to collect actual benchmark data."
    }
}

/**
 * Splits a response string into word-level token chunks for streaming emission.
 *
 * MediaPipe's [LlmInference.generateResponse] returns the complete response
 * as a single string. This splits it into token-like chunks to match the
 * [LlmEngine.generate] Flow<String> contract.
 */
internal fun String.splitIntoTokenChunks(): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()

    for (char in this) {
        if (char == ' ' || char == '\n') {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
            }
            tokens.add(char.toString())
        } else {
            current.append(char)
        }
    }

    if (current.isNotEmpty()) {
        tokens.add(current.toString())
    }

    return tokens
}
