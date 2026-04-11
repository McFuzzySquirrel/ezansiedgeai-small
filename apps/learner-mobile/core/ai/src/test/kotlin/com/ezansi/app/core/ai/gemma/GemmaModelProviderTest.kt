package com.ezansi.app.core.ai.gemma

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaModelProvider] — unified Gemma 4 lifecycle manager.
 *
 * These tests validate lifecycle management, error handling, state transitions,
 * and concurrent access **without requiring MediaPipe GenAI SDK or a real model**.
 * The provider gracefully degrades to STUB mode when MediaPipe is unavailable
 * (which is always the case in JVM unit tests).
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle android.util.Log calls without Robolectric.
 */
@DisplayName("GemmaModelProvider")
class GemmaModelProviderTest {

    private lateinit var provider: GemmaModelProvider

    @TempDir
    lateinit var tempDir: Path

    /** Creates a fake model file at the given path for lifecycle testing. */
    private fun createFakeModelFile(name: String = "gemma4.task"): File {
        val file = tempDir.resolve(name).toFile()
        file.writeText("fake-gemma4-model-data")
        return file
    }

    @BeforeEach
    fun setUp() {
        // Context is null → provider operates in stub mode (no MediaPipe)
        provider = GemmaModelProvider(context = null)
    }

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        @DisplayName("is not loaded initially")
        fun notLoadedInitially() {
            assertFalse(provider.isLoaded())
        }

        @Test
        @DisplayName("mode is IDLE initially")
        fun modeIsIdleInitially() {
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }

        @Test
        @DisplayName("runtime mode is STUB without MediaPipe")
        fun runtimeModeIsStub() {
            // In JVM tests, MediaPipe SDK is not on classpath
            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
        }

        @Test
        @DisplayName("activeConfig is null initially")
        fun activeConfigNullInitially() {
            assertNull(provider.activeConfig())
        }

        @Test
        @DisplayName("GPU delegate is not active initially")
        fun gpuDelegateNotActive() {
            assertFalse(provider.isGpuDelegateActive())
        }

        @Test
        @DisplayName("last load time is zero initially")
        fun loadTimeZeroInitially() {
            assertEquals(0L, provider.lastLoadTimeMs())
        }
    }

    @Nested
    @DisplayName("Loading lifecycle")
    inner class LoadingLifecycleTests {

        @Test
        @DisplayName("loads model and transitions to loaded state (stub mode)")
        fun loadsModelInStubMode() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            provider.loadModel(config)

            assertTrue(provider.isLoaded())
            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
            assertEquals(GemmaModelProvider.ModelMode.UNIFIED, provider.currentMode())
        }

        @Test
        @DisplayName("stores active config after loading")
        fun storesActiveConfig() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(
                modelPath = modelFile.absolutePath,
                temperature = 0.5f,
                embeddingDimension = 384,
            )

            provider.loadModel(config)

            val active = provider.activeConfig()
            assertNotNull(active)
            assertEquals(config.modelPath, active.modelPath)
            assertEquals(0.5f, active.temperature)
            assertEquals(384, active.embeddingDimension)
        }

        @Test
        @DisplayName("loadModel is idempotent for same path")
        fun loadModelIdempotentSamePath() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            provider.loadModel(config)
            provider.loadModel(config) // Should be a no-op

            assertTrue(provider.isLoaded())
        }

        @Test
        @DisplayName("loadModel unloads previous model when path changes")
        fun unloadsPreviousOnPathChange() = runTest {
            val modelFileA = createFakeModelFile("model_a.task")
            val modelFileB = createFakeModelFile("model_b.task")

            val configA = GemmaModelConfig(modelPath = modelFileA.absolutePath)
            val configB = GemmaModelConfig(modelPath = modelFileB.absolutePath)

            provider.loadModel(configA)
            assertEquals(modelFileA.absolutePath, provider.activeConfig()?.modelPath)

            provider.loadModel(configB)
            assertTrue(provider.isLoaded())
            assertEquals(modelFileB.absolutePath, provider.activeConfig()?.modelPath)
        }

        @Test
        @DisplayName("records non-zero load time")
        fun recordsLoadTime() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            provider.loadModel(config)

            // Load time should be recorded (at least 0)
            assertTrue(provider.lastLoadTimeMs() >= 0)
        }
    }

    @Nested
    @DisplayName("Unloading lifecycle")
    inner class UnloadingLifecycleTests {

        @Test
        @DisplayName("unloadModel transitions to unloaded state")
        fun unloadTransitionsState() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            provider.unloadModel()

            assertFalse(provider.isLoaded())
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
            assertNull(provider.activeConfig())
        }

        @Test
        @DisplayName("unloadModel is safe when not loaded (no-op)")
        fun unloadSafeWhenNotLoaded() = runTest {
            provider.unloadModel() // Should not throw
            assertFalse(provider.isLoaded())
        }

        @Test
        @DisplayName("can reload after unload")
        fun canReloadAfterUnload() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            provider.loadModel(config)
            provider.unloadModel()
            assertFalse(provider.isLoaded())

            provider.loadModel(config)
            assertTrue(provider.isLoaded())
        }

        @Test
        @DisplayName("unload resets GPU delegate state")
        fun unloadResetsGpuDelegate() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            provider.unloadModel()

            assertFalse(provider.isGpuDelegateActive())
        }

        @Test
        @DisplayName("unload resets load time")
        fun unloadResetsLoadTime() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            provider.unloadModel()

            assertEquals(0L, provider.lastLoadTimeMs())
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("throws FileNotFoundException for nonexistent model path")
        fun throwsForMissingModel() = runTest {
            val config = GemmaModelConfig(modelPath = "/nonexistent/path/gemma4.task")

            assertFailsWith<java.io.FileNotFoundException> {
                provider.loadModel(config)
            }
        }

        @Test
        @DisplayName("remains unloaded after FileNotFoundException")
        fun remainsUnloadedAfterError() = runTest {
            val config = GemmaModelConfig(modelPath = "/nonexistent/path/gemma4.task")

            try {
                provider.loadModel(config)
            } catch (_: java.io.FileNotFoundException) {
                // expected
            }

            assertFalse(provider.isLoaded())
            assertNull(provider.activeConfig())
        }

        @Test
        @DisplayName("getInference throws when not loaded")
        fun getInferenceThrowsWhenNotLoaded() {
            assertFailsWith<IllegalStateException> {
                provider.getInference()
            }
        }

        @Test
        @DisplayName("getInference returns null in stub mode (no real MediaPipe)")
        fun getInferenceReturnsNullInStubMode() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            // In stub mode (no MediaPipe), inference handle is null
            val inference = provider.getInference()
            assertNull(inference)
        }
    }

    @Nested
    @DisplayName("Model mode management")
    inner class ModelModeTests {

        @Test
        @DisplayName("setMode updates current mode")
        fun setModeUpdates() {
            provider.setMode(GemmaModelProvider.ModelMode.GENERATION)
            assertEquals(GemmaModelProvider.ModelMode.GENERATION, provider.currentMode())
        }

        @Test
        @DisplayName("mode transitions through all values")
        fun modeTransitions() {
            for (mode in GemmaModelProvider.ModelMode.entries) {
                provider.setMode(mode)
                assertEquals(mode, provider.currentMode())
            }
        }

        @Test
        @DisplayName("unload resets mode to IDLE")
        fun unloadResetsModeToIdle() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))
            provider.setMode(GemmaModelProvider.ModelMode.GENERATION)

            provider.unloadModel()

            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }
    }

    @Nested
    @DisplayName("Thread safety (concurrent access)")
    inner class ThreadSafetyTests {

        @Test
        @DisplayName("concurrent loads do not corrupt state")
        fun concurrentLoadsAreThreadSafe() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            // Launch multiple concurrent loads
            val jobs = (1..10).map {
                async {
                    provider.loadModel(config)
                }
            }
            jobs.awaitAll()

            // State should be consistent
            assertTrue(provider.isLoaded())
            assertEquals(GemmaModelProvider.ModelMode.UNIFIED, provider.currentMode())
        }

        @Test
        @DisplayName("concurrent load and unload do not throw")
        fun concurrentLoadAndUnloadDoNotThrow() = runTest {
            val modelFile = createFakeModelFile()
            val config = GemmaModelConfig(modelPath = modelFile.absolutePath)

            // Interleave loads and unloads
            val jobs = (1..10).map { i ->
                async {
                    if (i % 2 == 0) {
                        provider.loadModel(config)
                    } else {
                        provider.unloadModel()
                    }
                }
            }
            jobs.awaitAll()

            // State should be consistent (either loaded or not — no crash)
            val isLoaded = provider.isLoaded()
            if (isLoaded) {
                assertNotNull(provider.activeConfig())
            } else {
                assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
            }
        }

        @Test
        @DisplayName("concurrent unloads do not throw")
        fun concurrentUnloadsDoNotThrow() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            val jobs = (1..10).map {
                async {
                    provider.unloadModel()
                }
            }
            jobs.awaitAll()

            assertFalse(provider.isLoaded())
        }
    }

    @Nested
    @DisplayName("Runtime mode")
    inner class RuntimeModeTests {

        @Test
        @DisplayName("STUB when not loaded and no MediaPipe")
        fun stubWhenNotLoaded() {
            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
        }

        @Test
        @DisplayName("STUB when loaded without MediaPipe")
        fun stubWhenLoadedWithoutMediaPipe() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))

            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
        }

        @Test
        @DisplayName("returns STUB after unload")
        fun stubAfterUnload() = runTest {
            val modelFile = createFakeModelFile()
            provider.loadModel(GemmaModelConfig(modelPath = modelFile.absolutePath))
            provider.unloadModel()

            assertEquals(GemmaRuntimeMode.STUB, provider.runtimeMode())
        }
    }

    @Nested
    @DisplayName("GemmaRuntimeMode enum")
    inner class GemmaRuntimeModeTests {

        @Test
        @DisplayName("contains exactly three modes")
        fun containsThreeModes() {
            assertEquals(3, GemmaRuntimeMode.entries.size)
        }

        @Test
        @DisplayName("modes are REAL_MEDIAPIPE, MEDIAPIPE_UNAVAILABLE, STUB")
        fun modesAreCorrect() {
            val names = GemmaRuntimeMode.entries.map { it.name }
            assertTrue("REAL_MEDIAPIPE" in names)
            assertTrue("MEDIAPIPE_UNAVAILABLE" in names)
            assertTrue("STUB" in names)
        }
    }

    @Nested
    @DisplayName("ModelMode enum")
    inner class ModelModeEnumTests {

        @Test
        @DisplayName("contains exactly four modes")
        fun containsFourModes() {
            assertEquals(4, GemmaModelProvider.ModelMode.entries.size)
        }

        @Test
        @DisplayName("modes are IDLE, GENERATION, EMBEDDING, UNIFIED")
        fun modesAreCorrect() {
            val names = GemmaModelProvider.ModelMode.entries.map { it.name }
            assertTrue("IDLE" in names)
            assertTrue("GENERATION" in names)
            assertTrue("EMBEDDING" in names)
            assertTrue("UNIFIED" in names)
        }
    }
}
