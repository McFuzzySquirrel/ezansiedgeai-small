package com.ezansi.app.core.ai.gemma

import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaEmbeddingModel] — Gemma 4 embedding via shared [GemmaModelProvider].
 *
 * All tests run in stub mode (`GemmaModelProvider(context = null)`) since
 * the MediaPipe GenAI SDK is not available in JVM unit tests. The embedding
 * model uses deterministic hash-based vectors in all current paths, so the
 * tests validate dimension, normalisation, determinism, and lifecycle
 * delegation without requiring real model inference.
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle `android.util.Log` calls without Robolectric.
 */
@DisplayName("GemmaEmbeddingModel")
class GemmaEmbeddingModelTest {

    private lateinit var provider: GemmaModelProvider
    private lateinit var model: GemmaEmbeddingModel

    @TempDir
    lateinit var tempDir: Path

    /** Creates a fake model file for lifecycle testing. */
    private fun createFakeModelFile(name: String = "gemma4.task"): File {
        val file = tempDir.resolve(name).toFile()
        file.writeText("fake-gemma4-model-data")
        return file
    }

    @BeforeEach
    fun setUp() {
        provider = GemmaModelProvider(context = null)
        model = GemmaEmbeddingModel(
            modelProvider = provider,
            config = GemmaModelConfig(modelPath = "default"),
        )
    }

    // ── Initial state ───────────────────────────────────────────────

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        @DisplayName("isLoaded always returns true (hash-based embedding always available)")
        fun isLoadedAlwaysTrue() {
            assertTrue(model.isLoaded())
        }

        @Test
        @DisplayName("runtime mode is MOCK in stub provider")
        fun runtimeModeIsMockInStub() {
            assertEquals(EmbeddingRuntimeMode.MOCK, model.runtimeMode())
        }
    }

    // ── Loading lifecycle ───────────────────────────────────────────

    @Nested
    @DisplayName("Loading lifecycle")
    inner class LoadingLifecycleTests {

        @Test
        @DisplayName("loadModel delegates to provider and transitions to loaded")
        fun loadModelDelegatesToProvider() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            assertTrue(model.isLoaded())
            assertTrue(provider.isLoaded())
        }

        @Test
        @DisplayName("loadModel preserves embedding dimension from constructor config")
        fun loadModelPreservesDimension() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 384,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            assertEquals(384, provider.activeConfig()?.embeddingDimension)
        }

        @Test
        @DisplayName("isLoaded returns true regardless of provider state")
        fun isLoadedAlwaysTrue() = runTest {
            assertTrue(model.isLoaded())

            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)
            assertTrue(model.isLoaded())
        }

        @Test
        @DisplayName("loadModel throws for nonexistent file")
        fun loadModelThrowsForMissingFile() = runTest {
            assertFailsWith<java.io.FileNotFoundException> {
                model.loadModel("/nonexistent/path/gemma4.task")
            }
        }
    }

    // ── Embedding output ────────────────────────────────────────────

    @Nested
    @DisplayName("Embedding output")
    inner class EmbeddingOutputTests {

        @Test
        @DisplayName("embed produces vector of default dimension (768)")
        fun embedProducesDefaultDimension() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            val embedding = model.embed("What is a fraction?")

            assertEquals(768, embedding.size)
        }

        @Test
        @DisplayName("embed produces vector of custom dimension (384)")
        fun embedProducesCustomDimension() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 384,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            val embedding = customModel.embed("What is a fraction?")

            assertEquals(384, embedding.size)
        }

        @Test
        @DisplayName("embed produces vector of dimension 256")
        fun embedProducesDimension256() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 256,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            val embedding = customModel.embed("What is a fraction?")

            assertEquals(256, embedding.size)
        }

        @Test
        @DisplayName("embed produces vector of dimension 512")
        fun embedProducesDimension512() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 512,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            val embedding = customModel.embed("What is a fraction?")

            assertEquals(512, embedding.size)
        }

        @Test
        @DisplayName("embed output is L2-normalised (dot product with self ≈ 1.0)")
        fun embedOutputIsNormalised() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            val embedding = model.embed("What is a fraction?")

            val dotProduct = embedding.map { it.toDouble() * it.toDouble() }.sum()
            assertTrue(
                abs(dotProduct - 1.0) < 1e-5,
                "Expected dot product ≈ 1.0, got $dotProduct",
            )
        }

        @Test
        @DisplayName("embed output is L2-normalised for 384-dim")
        fun embedOutputIsNormalised384() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 384,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            val embedding = customModel.embed("Explain addition")

            val dotProduct = embedding.map { it.toDouble() * it.toDouble() }.sum()
            assertTrue(
                abs(dotProduct - 1.0) < 1e-5,
                "Expected dot product ≈ 1.0, got $dotProduct",
            )
        }

        @Test
        @DisplayName("embed is deterministic — same text produces same vector")
        fun embedIsDeterministic() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            val embedding1 = model.embed("What is a fraction?")
            val embedding2 = model.embed("What is a fraction?")

            assertTrue(
                embedding1.contentEquals(embedding2),
                "Same input text must produce identical embedding vectors",
            )
        }

        @Test
        @DisplayName("embed with different text produces different vector")
        fun differentTextProducesDifferentVector() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            val embedding1 = model.embed("What is a fraction?")
            val embedding2 = model.embed("Explain multiplication")

            assertFalse(
                embedding1.contentEquals(embedding2),
                "Different input text must produce different embedding vectors",
            )
        }

        @Test
        @DisplayName("embed contains no NaN or Infinity values")
        fun embedContainsNoSpecialValues() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            val embedding = model.embed("Test input")

            for (i in embedding.indices) {
                assertFalse(embedding[i].isNaN(), "Element $i is NaN")
                assertFalse(embedding[i].isInfinite(), "Element $i is Infinite")
            }
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("embed works even when provider model is not loaded (hash-based)")
        fun embedWorksWithoutProviderLoaded() = runTest {
            // Hash-based embedding should work regardless of model load state
            val result = model.embed("What is a fraction?")
            assertEquals(768, result.size) // default dimension
        }

        @Test
        @DisplayName("embed produces valid L2-normalised output without model loaded")
        fun embedProducesValidOutputWithoutModel() = runTest {
            val result = model.embed("test")
            // Verify L2 normalisation
            var sumSquares = 0.0f
            for (v in result) sumSquares += v * v
            val magnitude = sqrt(sumSquares.toDouble()).toFloat()
            assertTrue(
                abs(magnitude - 1.0f) < 0.01f,
                "Embedding should be L2-normalised, got magnitude: $magnitude",
            )
        }
    }

    // ── Unload behaviour ────────────────────────────────────────────

    @Nested
    @DisplayName("Unload behaviour")
    inner class UnloadBehaviourTests {

        @Test
        @DisplayName("unload sets mode to IDLE")
        fun unloadSetsModeToIdle() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)
            model.embed("trigger embedding mode")

            assertEquals(GemmaModelProvider.ModelMode.EMBEDDING, provider.currentMode())

            model.unload()

            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }

        @Test
        @DisplayName("unload does NOT unload the shared provider model")
        fun unloadDoesNotUnloadProvider() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            model.unload()

            // Provider should still be loaded — model is shared
            assertTrue(
                provider.isLoaded(),
                "unload() must NOT unload the shared provider model",
            )
        }

        @Test
        @DisplayName("unload is safe to call when not loaded")
        fun unloadSafeWhenNotLoaded() {
            // Should not throw
            model.unload()
            assertEquals(GemmaModelProvider.ModelMode.IDLE, provider.currentMode())
        }
    }

    // ── Mode tracking ───────────────────────────────────────────────

    @Nested
    @DisplayName("Mode tracking")
    inner class ModeTrackingTests {

        @Test
        @DisplayName("embed sets provider mode to EMBEDDING")
        fun embedSetsModeToEmbedding() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            model.embed("What is a fraction?")

            assertEquals(GemmaModelProvider.ModelMode.EMBEDDING, provider.currentMode())
        }
    }

    // ── Runtime mode mapping ────────────────────────────────────────

    @Nested
    @DisplayName("Runtime mode mapping")
    inner class RuntimeModeTests {

        @Test
        @DisplayName("returns MOCK when provider is in STUB mode")
        fun mockWhenProviderIsStub() {
            // Provider created with context=null → STUB mode
            assertEquals(EmbeddingRuntimeMode.MOCK, model.runtimeMode())
        }

        @Test
        @DisplayName("returns MOCK after loading in STUB mode")
        fun mockAfterLoadInStubMode() = runTest {
            val modelFile = createFakeModelFile()
            model.loadModel(modelFile.absolutePath)

            // Still MOCK because provider is STUB
            assertEquals(EmbeddingRuntimeMode.MOCK, model.runtimeMode())
        }
    }

    // ── Embedding dimension ─────────────────────────────────────────

    @Nested
    @DisplayName("Embedding dimension")
    inner class EmbeddingDimensionTests {

        @Test
        @DisplayName("uses constructor config dimension before load")
        fun usesConstructorConfigDimension() {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 384,
                ),
            )
            assertEquals(384, customModel.embeddingDimension())
        }

        @Test
        @DisplayName("uses provider active config dimension after load")
        fun usesProviderConfigDimensionAfterLoad() = runTest {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 384,
                ),
            )
            val modelFile = createFakeModelFile()
            customModel.loadModel(modelFile.absolutePath)

            // After load, provider's active config dimension should be used
            assertEquals(384, customModel.embeddingDimension())
        }

        @Test
        @DisplayName("falls back to constructor config when provider has no active config")
        fun fallsBackToConstructorConfig() {
            val customModel = GemmaEmbeddingModel(
                modelProvider = provider,
                config = GemmaModelConfig(
                    modelPath = "default",
                    embeddingDimension = 512,
                ),
            )

            // Provider not loaded → no active config → use constructor config
            assertEquals(512, customModel.embeddingDimension())
        }
    }
}
