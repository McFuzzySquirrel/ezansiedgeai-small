package com.ezansi.app.core.ai.gemma

/**
 * Runtime mode for the unified Gemma 4 model provider.
 *
 * Tracks the underlying inference backend state at the [GemmaModelProvider]
 * level, independent of the per-engine modes ([LlmRuntimeMode],
 * [EmbeddingRuntimeMode]) which report on individual implementations.
 *
 * ## Mode Selection
 *
 * ```
 * REAL_MEDIAPIPE         — MediaPipe GenAI SDK loaded, model file found and loaded
 * MEDIAPIPE_UNAVAILABLE  — MediaPipe SDK on classpath but model file missing/failed
 * STUB                   — No MediaPipe SDK (JVM unit tests, emulator without libs)
 * ```
 *
 * @see GemmaModelProvider
 */
enum class GemmaRuntimeMode {

    /**
     * Real MediaPipe GenAI inference is available and the model is loaded.
     *
     * This mode is active on-device when the Gemma 4 `.task` model file is
     * present and the MediaPipe SDK has been initialised successfully.
     */
    REAL_MEDIAPIPE,

    /**
     * MediaPipe SDK is on the classpath but the model could not be loaded.
     *
     * This can occur when:
     * - The model file does not exist at the configured path
     * - The model file is corrupted or incompatible
     * - GPU delegate fails and CPU fallback also fails
     */
    MEDIAPIPE_UNAVAILABLE,

    /**
     * Stub mode for unit testing without the MediaPipe SDK or model files.
     *
     * In this mode, the provider accepts load/unload calls but does not hold
     * a real model. Consumers (GemmaLiteRtEngine, GemmaEmbeddingModel) should
     * produce deterministic stub output when the provider is in STUB mode.
     */
    STUB,
}
