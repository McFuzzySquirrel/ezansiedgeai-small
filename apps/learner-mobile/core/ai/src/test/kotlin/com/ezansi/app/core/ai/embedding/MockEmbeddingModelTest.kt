package com.ezansi.app.core.ai.embedding

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
 * Unit tests for [MockEmbeddingModel] — the deterministic test embedding model.
 *
 * Validates that the mock model:
 * - Produces 384-dimensional L2-normalised vectors
 * - Returns deterministic output (same input → same output)
 * - Requires explicit loading before use
 * - Supports the load/unload lifecycle
 *
 * These tests use `testOptions.unitTests.isReturnDefaultValues = true`
 * to handle android.util.Log calls without Robolectric.
 */
@DisplayName("MockEmbeddingModel")
class MockEmbeddingModelTest {

    private lateinit var model: MockEmbeddingModel

    @BeforeEach
    fun setUp() {
        model = MockEmbeddingModel()
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
            model.loadModel("/fake/path/model.onnx")
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
    }

    @Nested
    @DisplayName("Embedding generation")
    inner class EmbeddingGenerationTests {

        @BeforeEach
        fun loadModel() = runTest {
            model.loadModel("/fake/path")
        }

        @Test
        @DisplayName("produces 384-dimensional vector")
        fun produces384DimensionalVector() = runTest {
            val embedding = model.embed("What are fractions?")
            assertEquals(MockEmbeddingModel.EMBEDDING_DIMENSION, embedding.size)
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
}
