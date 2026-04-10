package com.ezansi.app.core.ai.spike

import com.ezansi.app.core.ai.inference.LlmRuntimeMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaSpikeLlmEngine] — P0-006 spike LLM engine.
 *
 * These tests validate interface compliance, error handling, and metrics
 * collection **without requiring the MediaPipe GenAI SDK or a real model**.
 * The engine gracefully degrades to stub mode when MediaPipe is unavailable
 * (which is always the case in JVM unit tests).
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle android.util.Log and android.os.Debug calls without Robolectric.
 */
@DisplayName("GemmaSpikeLlmEngine")
class GemmaSpikeLlmEngineTest {

    private lateinit var engine: GemmaSpikeLlmEngine

    @BeforeEach
    fun setUp() {
        // Context is null → engine operates in stub mode (no MediaPipe)
        engine = GemmaSpikeLlmEngine(context = null, useGpuDelegate = true)
    }

    @Nested
    @DisplayName("Loading lifecycle")
    inner class LoadingLifecycleTests {

        @Test
        @DisplayName("is not loaded initially")
        fun notLoadedInitially() {
            assertFalse(engine.isLoaded())
        }

        @Test
        @DisplayName("reports NATIVE_UNAVAILABLE runtime mode without MediaPipe")
        fun reportsNativeUnavailableMode() {
            assertEquals(LlmRuntimeMode.NATIVE_UNAVAILABLE, engine.runtimeMode())
        }

        @Test
        @DisplayName("loadModel fails with FileNotFoundException for missing model")
        fun loadModelFailsForMissingModel() = runTest {
            assertFailsWith<java.io.FileNotFoundException> {
                engine.loadModel("/nonexistent/path/gemma4-1b-it-int4.task")
            }
        }

        @Test
        @DisplayName("is not loaded after failed loadModel")
        fun notLoadedAfterFailedLoad() = runTest {
            try {
                engine.loadModel("/nonexistent/model.task")
            } catch (_: java.io.FileNotFoundException) {
                // expected
            }
            assertFalse(engine.isLoaded())
        }

        @Test
        @DisplayName("unload is safe when not loaded")
        fun unloadSafeWhenNotLoaded() {
            engine.unload() // Should not throw
            assertFalse(engine.isLoaded())
        }
    }

    @Nested
    @DisplayName("Generation (stub mode)")
    inner class StubGenerationTests {

        @Test
        @DisplayName("throws IllegalStateException when generating before loading")
        fun throwsWhenNotLoaded() = runTest {
            assertFailsWith<IllegalStateException> {
                engine.generate("test prompt").toList()
            }
        }
    }

    @Nested
    @DisplayName("Spike metrics")
    inner class SpikeMetricsTests {

        @Test
        @DisplayName("initial metrics are zero")
        fun initialMetricsAreZero() {
            assertEquals(0L, engine.lastLoadTimeMs())
            assertEquals(0L, engine.lastGenerationTimeMs())
            assertEquals(0, engine.lastTokenCount())
        }

        @Test
        @DisplayName("GPU delegate reports false when context is null")
        fun gpuDelegateInactive() {
            assertFalse(engine.isGpuDelegateActive())
        }
    }

    @Nested
    @DisplayName("Token splitting")
    inner class TokenSplittingTests {

        @Test
        @DisplayName("splits simple text into word tokens")
        fun splitsSimpleText() {
            val tokens = "Hello world".splitIntoTokenChunks()
            assertEquals(listOf("Hello", " ", "world"), tokens)
        }

        @Test
        @DisplayName("handles newlines as separate tokens")
        fun handlesNewlines() {
            val tokens = "Line1\nLine2".splitIntoTokenChunks()
            assertEquals(listOf("Line1", "\n", "Line2"), tokens)
        }

        @Test
        @DisplayName("handles empty string")
        fun handlesEmptyString() {
            val tokens = "".splitIntoTokenChunks()
            assertTrue(tokens.isEmpty())
        }

        @Test
        @DisplayName("preserves consecutive spaces as individual tokens")
        fun preservesConsecutiveSpaces() {
            val tokens = "a  b".splitIntoTokenChunks()
            assertEquals(listOf("a", " ", " ", "b"), tokens)
        }
    }
}
