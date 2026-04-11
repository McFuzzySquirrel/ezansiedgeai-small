package com.ezansi.app.core.ai.spike

import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaSpikeEmbeddingModel] — P0-006 spike embedding model.
 *
 * Validates interface compliance, configurable dimensions, L2 normalisation,
 * deterministic output, and metrics collection. All tests run without a real
 * model file since the implementation uses hash-based deterministic embeddings.
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle android.util.Log calls without Robolectric.
 */
@DisplayName("GemmaSpikeEmbeddingModel")
class GemmaSpikeEmbeddingModelTest {

    private lateinit var model: GemmaSpikeEmbeddingModel

    @BeforeEach
    fun setUp() {
        model = GemmaSpikeEmbeddingModel(embeddingDimension = 384)
    }

    @Nested
    @DisplayName("Loading lifecycle")
    inner class LoadingLifecycleTests {

        @Test
        @DisplayName("is not loaded initially")
        fun notLoadedInitially() {
            assertFalse(model.isLoaded())
        }

        @Test
        @DisplayName("is loaded after loadModel")
        fun loadedAfterLoadModel() = runTest {
            model.loadModel("/fake/path/gemma4.task")
            assertTrue(model.isLoaded())
        }

        @Test
        @DisplayName("is not loaded after unload")
        fun notLoadedAfterUnload() = runTest {
            model.loadModel("/fake/path")
            model.unload()
            assertFalse(model.isLoaded())
        }

        @Test
        @DisplayName("loadModel is idempotent")
        fun loadModelIdempotent() = runTest {
            model.loadModel("/path1")
            model.loadModel("/path2")
            assertTrue(model.isLoaded())
        }

        @Test
        @DisplayName("unload is safe when not loaded")
        fun unloadSafeWhenNotLoaded() {
            model.unload() // Should not throw
            assertFalse(model.isLoaded())
        }

        @Test
        @DisplayName("reports DETERMINISTIC_FALLBACK runtime mode (no real Gemma embeddings)")
        fun reportsDeterministicFallbackMode() = runTest {
            model.loadModel("/fake/path")
            assertEquals(EmbeddingRuntimeMode.DETERMINISTIC_FALLBACK, model.runtimeMode())
        }
    }

    @Nested
    @DisplayName("Embedding generation")
    inner class EmbeddingGenerationTests {

        @BeforeEach
        fun loadModel() = runTest {
            model.loadModel("/fake/path")
        }

        @Test
        @DisplayName("produces vector with configured dimension (384)")
        fun produces384DimensionalVector() = runTest {
            val embedding = model.embed("What are fractions?")
            assertEquals(384, embedding.size)
        }

        @Test
        @DisplayName("embedding is L2-normalised (magnitude ≈ 1.0)")
        fun embeddingIsL2Normalised() = runTest {
            val embedding = model.embed("How do I add fractions?")

            var sumSquares = 0.0
            for (value in embedding) {
                sumSquares += value.toDouble() * value.toDouble()
            }
            val magnitude = sqrt(sumSquares)

            assertTrue(
                abs(magnitude - 1.0) < 0.001,
                "Expected L2-normalised magnitude ≈ 1.0, got $magnitude",
            )
        }

        @Test
        @DisplayName("same input produces same output (deterministic)")
        fun sameInputSameOutput() = runTest {
            val first = model.embed("What is 1/2 + 1/4?")
            val second = model.embed("What is 1/2 + 1/4?")

            assertTrue(first.contentEquals(second), "Same input should produce same embedding")
        }

        @Test
        @DisplayName("different input produces different output")
        fun differentInputDifferentOutput() = runTest {
            val fractions = model.embed("What are fractions?")
            val geometry = model.embed("What are angles?")

            assertFalse(
                fractions.contentEquals(geometry),
                "Different inputs should produce different embeddings",
            )
        }

        @Test
        @DisplayName("is case-normalised (uppercase/lowercase same result)")
        fun caseNormalised() = runTest {
            val lower = model.embed("fractions")
            val upper = model.embed("FRACTIONS")

            assertTrue(lower.contentEquals(upper), "Case should be normalised")
        }

        @Test
        @DisplayName("trims whitespace before embedding")
        fun trimsWhitespace() = runTest {
            val trimmed = model.embed("fractions")
            val padded = model.embed("  fractions  ")

            assertTrue(trimmed.contentEquals(padded), "Whitespace should be trimmed")
        }
    }

    @Nested
    @DisplayName("Configurable dimensions")
    inner class ConfigurableDimensionTests {

        @Test
        @DisplayName("supports 256-dimensional output")
        fun supports256Dimensions() = runTest {
            val model256 = GemmaSpikeEmbeddingModel(embeddingDimension = 256)
            model256.loadModel("/fake/path")
            val embedding = model256.embed("test")
            assertEquals(256, embedding.size)
            assertEquals(256, model256.dimension())
        }

        @Test
        @DisplayName("supports 512-dimensional output")
        fun supports512Dimensions() = runTest {
            val model512 = GemmaSpikeEmbeddingModel(embeddingDimension = 512)
            model512.loadModel("/fake/path")
            val embedding = model512.embed("test")
            assertEquals(512, embedding.size)
        }

        @Test
        @DisplayName("supports 768-dimensional output (Gemma 4 native)")
        fun supports768Dimensions() = runTest {
            val model768 = GemmaSpikeEmbeddingModel(embeddingDimension = 768)
            model768.loadModel("/fake/path")
            val embedding = model768.embed("test")
            assertEquals(768, embedding.size)
        }

        @Test
        @DisplayName("rejects unsupported dimensions")
        fun rejectsUnsupportedDimensions() {
            assertFailsWith<IllegalArgumentException> {
                GemmaSpikeEmbeddingModel(embeddingDimension = 100)
            }
        }

        @Test
        @DisplayName("all supported dimensions produce L2-normalised vectors")
        fun allDimensionsNormalised() = runTest {
            for (dim in GemmaSpikeEmbeddingModel.SUPPORTED_DIMENSIONS) {
                val dimModel = GemmaSpikeEmbeddingModel(embeddingDimension = dim)
                dimModel.loadModel("/fake/path")
                val embedding = dimModel.embed("test normalisation at $dim dimensions")

                var sumSquares = 0.0
                for (value in embedding) {
                    sumSquares += value.toDouble() * value.toDouble()
                }
                val magnitude = sqrt(sumSquares)

                assertTrue(
                    abs(magnitude - 1.0) < 0.001,
                    "Dimension $dim: Expected magnitude ≈ 1.0, got $magnitude",
                )
            }
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("throws when embedding before loading")
        fun throwsWhenNotLoaded() = runTest {
            assertFailsWith<IllegalStateException> {
                model.embed("test")
            }
        }

        @Test
        @DisplayName("throws after unload")
        fun throwsAfterUnload() = runTest {
            model.loadModel("/fake")
            model.unload()

            assertFailsWith<IllegalStateException> {
                model.embed("test")
            }
        }
    }

    @Nested
    @DisplayName("Spike metrics")
    inner class SpikeMetricsTests {

        @Test
        @DisplayName("tracks embedding count")
        fun tracksEmbeddingCount() = runTest {
            model.loadModel("/fake/path")
            assertEquals(0, model.totalEmbeddings())

            model.embed("first")
            assertEquals(1, model.totalEmbeddings())

            model.embed("second")
            assertEquals(2, model.totalEmbeddings())
        }

        @Test
        @DisplayName("resets metrics on unload")
        fun resetsMetricsOnUnload() = runTest {
            model.loadModel("/fake/path")
            model.embed("test")
            model.unload()

            // After reload, counters should be reset
            model.loadModel("/fake/path")
            assertEquals(0, model.totalEmbeddings())
        }

        @Test
        @DisplayName("average embed time is zero with no embeddings")
        fun averageEmbedTimeZeroInitially() {
            assertEquals(0.0, model.averageEmbedTimeMs())
        }

        @Test
        @DisplayName("dimension accessor returns configured dimension")
        fun dimensionAccessor() {
            assertEquals(384, model.dimension())
        }
    }
}
