package com.ezansi.app.core.ai.gemma

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Unified lifecycle manager for the Gemma 4 model loaded via MediaPipe GenAI SDK.
 *
 * The provider holds a **single** MediaPipe `LlmInference` instance that serves
 * both LLM generation and embedding extraction. It is the owner of the model's
 * lifecycle: load, access, and unload. The implementations `GemmaLiteRtEngine`
 * (F2.3) and `GemmaEmbeddingModel` (F2.4) delegate to this provider.
 *
 * ## Shared Singleton Pattern
 *
 * `GemmaModelProvider` is created once in `AppContainer` and injected into both
 * the LLM engine and the embedding model. This ensures:
 * - Only one copy of the ~600 MB model is ever in RAM
 * - Load/unload is coordinated — no accidental double-load
 * - GPU delegate configuration is consistent across consumers
 *
 * ## RAM Budget
 *
 * With unified loading, peak model RAM stays ≤1,200 MB (FT-NF-04). The
 * sequential load/unload pattern of the legacy pipeline (embedding unload →
 * LLM load) is **not** needed — both generation and embedding share the same
 * loaded model. The provider tracks which [ModelMode] is currently active so
 * callers can verify compatibility.
 *
 * ## Thread Safety
 *
 * All mutable state is guarded by a [Mutex]. Multiple coroutines can safely
 * call [loadModel], [unloadModel], and [getInference] concurrently. The mutex
 * is non-reentrant; callers must not hold the lock across suspending calls
 * that re-enter the provider.
 *
 * ## Stub Mode
 *
 * When the MediaPipe GenAI SDK is not on the classpath (JVM unit tests) or
 * the Android [Context] is null, the provider operates in [GemmaRuntimeMode.STUB]
 * mode. It accepts [loadModel] / [unloadModel] calls and tracks lifecycle state,
 * but [getInference] returns `null`. Consumers must handle the null case by
 * producing deterministic stub output.
 *
 * @param context Android application context for MediaPipe initialisation.
 *                Pass `null` for unit tests — the provider will operate in stub mode.
 *
 * @see GemmaModelConfig
 * @see GemmaRuntimeMode
 */
class GemmaModelProvider(
    private val context: Context?,
) {

    companion object {
        private const val TAG = "GemmaModelProvider"

        /** Gemma 4 context window size in tokens (PT-04). */
        internal const val CONTEXT_SIZE = 2048
    }

    /**
     * Tracks the current usage mode of the loaded model.
     *
     * While the unified model supports both generation and embedding
     * simultaneously, the mode helps observability and debugging by
     * indicating the last operation type.
     */
    enum class ModelMode {
        /** No model is loaded. */
        IDLE,

        /** Model loaded and available for generation. */
        GENERATION,

        /** Model loaded and available for embedding. */
        EMBEDDING,

        /** Model loaded and available for both generation and embedding. */
        UNIFIED,
    }

    // ── State (guarded by [mutex]) ──────────────────────────────────

    private val mutex = Mutex()

    /** MediaPipe LlmInference handle — held as Any? to avoid hard compile-time dependency. */
    private var llmInference: Any? = null

    /** Active config for the currently loaded model. */
    private var activeConfig: GemmaModelConfig? = null

    /** Whether a model is currently loaded and ready. */
    private var loaded: Boolean = false

    /** Current usage mode. */
    private var currentMode: ModelMode = ModelMode.IDLE

    /** Whether MediaPipe GenAI SDK is available on the classpath. */
    private val mediaPipeAvailable: Boolean = checkMediaPipeAvailability()

    /** Whether GPU delegate is actively being used. */
    private var gpuDelegateActive: Boolean = false

    /** Model load time from the last [loadModel] call, in milliseconds. */
    private var lastLoadTimeMs: Long = 0L

    init {
        if (!mediaPipeAvailable) {
            Log.w(
                TAG,
                "MediaPipe GenAI SDK not available on classpath — " +
                    "provider will operate in STUB mode.",
            )
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Loads the Gemma 4 model from the path specified in [config].
     *
     * If a model is already loaded with the same [GemmaModelConfig.modelPath],
     * this call is a no-op (the existing model is reused). If a model is loaded
     * with a *different* path, it is unloaded first.
     *
     * In stub mode (no MediaPipe or null context), this records the config and
     * transitions to loaded state without actually loading a model.
     *
     * @param config Configuration including model path, GPU settings, and parameters.
     * @throws java.io.FileNotFoundException if the model file does not exist.
     * @throws IllegalStateException if the model cannot be loaded and fallback fails.
     */
    suspend fun loadModel(config: GemmaModelConfig) {
        mutex.withLock {
            // Already loaded with the same path — reuse
            if (loaded && activeConfig?.modelPath == config.modelPath) {
                Log.d(TAG, "Model already loaded from: ${config.modelPath}, reusing")
                return
            }

            // Loaded with a different path — unload first
            if (loaded) {
                Log.i(TAG, "Unloading previous model before loading new config")
                doUnload()
            }

            val modelFile = File(config.modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${config.modelPath}")
                throw java.io.FileNotFoundException(
                    "Gemma 4 model not found at: ${config.modelPath}. " +
                        "Download the model file first.",
                )
            }

            Log.i(
                TAG,
                "Loading Gemma 4 model from: ${config.modelPath} " +
                    "(GPU: ${config.useGpuDelegate}, dim: ${config.embeddingDimension})",
            )
            val loadStart = System.currentTimeMillis()

            if (!mediaPipeAvailable || context == null) {
                Log.w(TAG, "MediaPipe unavailable or no Context — entering STUB mode")
                activeConfig = config
                loaded = true
                currentMode = ModelMode.UNIFIED
                lastLoadTimeMs = System.currentTimeMillis() - loadStart
                return
            }

            try {
                llmInference = createLlmInference(config)
                gpuDelegateActive = config.useGpuDelegate
                activeConfig = config
                loaded = true
                currentMode = ModelMode.UNIFIED
                lastLoadTimeMs = System.currentTimeMillis() - loadStart
                Log.i(
                    TAG,
                    "Gemma 4 model loaded in ${lastLoadTimeMs}ms " +
                        "(GPU delegate: $gpuDelegateActive)",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Gemma 4 model with GPU delegate", e)

                if (config.useGpuDelegate && config.gpuFallbackToCpu) {
                    Log.i(TAG, "Retrying with CPU-only execution")
                    try {
                        val cpuConfig = config.copy(useGpuDelegate = false)
                        llmInference = createLlmInference(cpuConfig)
                        gpuDelegateActive = false
                        activeConfig = cpuConfig
                        loaded = true
                        currentMode = ModelMode.UNIFIED
                        lastLoadTimeMs = System.currentTimeMillis() - loadStart
                        Log.i(
                            TAG,
                            "Gemma 4 model loaded (CPU fallback) in ${lastLoadTimeMs}ms",
                        )
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "CPU fallback also failed", fallbackError)
                        throw IllegalStateException(
                            "Failed to load Gemma 4 model from: ${config.modelPath} " +
                                "(both GPU and CPU paths failed)",
                            fallbackError,
                        )
                    }
                } else {
                    throw IllegalStateException(
                        "Failed to load Gemma 4 model from: ${config.modelPath}",
                        e,
                    )
                }
            }
        }
    }

    /**
     * Unloads the model and frees all associated resources.
     *
     * After unloading, [isLoaded] returns false and [getInference] returns null.
     * Safe to call when no model is loaded (no-op).
     */
    suspend fun unloadModel() {
        mutex.withLock {
            doUnload()
        }
    }

    /**
     * Returns true if a model is currently loaded and ready for inference.
     */
    fun isLoaded(): Boolean = loaded

    /**
     * Returns the current runtime mode of the provider.
     *
     * When the MediaPipe SDK is unavailable or no Android [Context] was provided
     * (null context = unit tests / emulator without native libs), the provider
     * is always in [GemmaRuntimeMode.STUB] — real inference is impossible.
     */
    fun runtimeMode(): GemmaRuntimeMode {
        return when {
            // No SDK or no Context → can never do real inference this session
            !mediaPipeAvailable || context == null -> GemmaRuntimeMode.STUB
            // SDK + Context + loaded with real inference handle
            loaded && llmInference != null -> GemmaRuntimeMode.REAL_MEDIAPIPE
            // SDK + Context, but model not loaded or load failed
            else -> GemmaRuntimeMode.MEDIAPIPE_UNAVAILABLE
        }
    }

    /**
     * Returns the underlying MediaPipe `LlmInference` handle, or null in stub mode.
     *
     * Callers **must** check for null and provide stub/fallback behaviour.
     * The returned object is typed as `Any?` to avoid a hard compile-time
     * dependency on the MediaPipe SDK in unit test environments.
     *
     * @throws IllegalStateException if no model is loaded.
     */
    fun getInference(): Any? {
        check(loaded) {
            "No Gemma 4 model is loaded. Call loadModel() first."
        }
        return llmInference
    }

    /**
     * Returns the active [GemmaModelConfig], or null if no model is loaded.
     */
    fun activeConfig(): GemmaModelConfig? = activeConfig

    /**
     * Returns the current model usage mode.
     */
    fun currentMode(): ModelMode = currentMode

    /**
     * Updates the current model usage mode.
     *
     * Called by GemmaLiteRtEngine and GemmaEmbeddingModel to signal which
     * operation is in progress, for observability.
     */
    fun setMode(mode: ModelMode) {
        currentMode = mode
    }

    /**
     * Whether GPU delegate is actively being used for the loaded model.
     */
    fun isGpuDelegateActive(): Boolean = gpuDelegateActive

    /**
     * Model load time in milliseconds from the last [loadModel] call.
     */
    fun lastLoadTimeMs(): Long = lastLoadTimeMs

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Internal unload — must be called under [mutex] lock.
     */
    private fun doUnload() {
        if (!loaded) return

        try {
            llmInference?.let { inference ->
                try {
                    inference.javaClass.getMethod("close").invoke(inference)
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing MediaPipe LlmInference", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during model unload", e)
        }

        llmInference = null
        activeConfig = null
        loaded = false
        currentMode = ModelMode.IDLE
        gpuDelegateActive = false
        lastLoadTimeMs = 0L
        Log.i(TAG, "Gemma 4 model unloaded — RAM freed")
    }

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
            false
        }
    }

    /**
     * Creates a MediaPipe LlmInference instance via reflection.
     *
     * Uses the SDK 0.10.33 builder API where model-level options are:
     * ```
     * LlmInference.LlmInferenceOptions.builder()
     *     .setModelPath(modelPath)
     *     .setMaxTokens(contextSize)
     *     .setPreferredBackend(Backend.GPU | Backend.CPU)
     *     .build()
     * LlmInference.createFromOptions(context, options)
     * ```
     *
     * Note: temperature, topK, and randomSeed moved to
     * `LlmInferenceSession.LlmInferenceSessionOptions` in SDK 0.10.33.
     * The implicit session used by `LlmInference.generateResponse()` uses
     * sensible defaults.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createLlmInference(config: GemmaModelConfig): Any {
        val inferenceClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference",
        )
        val optionsClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
        )
        val builderClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder",
        )
        val backendClass = Class.forName(
            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Backend",
        )

        val builderMethod = optionsClass.getMethod("builder")
        val builder = builderMethod.invoke(null)

        builderClass.getMethod("setModelPath", String::class.java)
            .invoke(builder, config.modelPath)
        builderClass.getMethod("setMaxTokens", Int::class.java)
            .invoke(builder, CONTEXT_SIZE)

        // Set preferred backend via the Backend enum.
        // Use CPU when GPU is explicitly disabled; otherwise let SDK auto-detect
        // with DEFAULT to avoid native crashes on devices without GPU support.
        val backendName = if (config.useGpuDelegate) "DEFAULT" else "CPU"
        val backendValue = backendClass.getMethod("valueOf", String::class.java)
            .invoke(null, backendName)
        builderClass.getMethod("setPreferredBackend", backendClass)
            .invoke(builder, backendValue)

        val options = builderClass.getMethod("build").invoke(builder)

        val createMethod = inferenceClass.getMethod(
            "createFromOptions",
            Context::class.java,
            optionsClass,
        )
        return createMethod.invoke(null, context, options)
            ?: throw IllegalStateException("createFromOptions returned null")
    }
}
