package com.ezansi.app.core.ai.gemma

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [GemmaModelConfig] — configuration data class.
 *
 * Validates default values, validation rules, and copy behaviour.
 * These tests run as pure JVM tests with no Android or model dependencies.
 */
@DisplayName("GemmaModelConfig")
class GemmaModelConfigTest {

    @Nested
    @DisplayName("Default values")
    inner class DefaultValueTests {

        @Test
        @DisplayName("uses correct defaults for all optional fields")
        fun usesCorrectDefaults() {
            val config = GemmaModelConfig(modelPath = "/data/models/gemma4.task")

            assertEquals("/data/models/gemma4.task", config.modelPath)
            assertEquals(512, config.maxTokens)
            assertEquals(0.3f, config.temperature)
            assertEquals(40, config.topK)
            assertEquals(42, config.randomSeed)
            assertTrue(config.useGpuDelegate)
            assertTrue(config.gpuFallbackToCpu)
            assertEquals(768, config.embeddingDimension)
        }

        @Test
        @DisplayName("companion constants match defaults")
        fun companionConstantsMatchDefaults() {
            assertEquals(512, GemmaModelConfig.DEFAULT_MAX_TOKENS)
            assertEquals(0.3f, GemmaModelConfig.DEFAULT_TEMPERATURE)
            assertEquals(40, GemmaModelConfig.DEFAULT_TOP_K)
            assertEquals(42, GemmaModelConfig.DEFAULT_RANDOM_SEED)
            assertEquals(768, GemmaModelConfig.DEFAULT_EMBEDDING_DIMENSION)
        }
    }

    @Nested
    @DisplayName("Custom values")
    inner class CustomValueTests {

        @Test
        @DisplayName("accepts all custom values")
        fun acceptsCustomValues() {
            val config = GemmaModelConfig(
                modelPath = "/sdcard/gemma4-1b-it-int4.task",
                maxTokens = 150,
                temperature = 0.7f,
                topK = 20,
                randomSeed = 123,
                useGpuDelegate = false,
                gpuFallbackToCpu = false,
                embeddingDimension = 384,
            )

            assertEquals("/sdcard/gemma4-1b-it-int4.task", config.modelPath)
            assertEquals(150, config.maxTokens)
            assertEquals(0.7f, config.temperature)
            assertEquals(20, config.topK)
            assertEquals(123, config.randomSeed)
            assertEquals(false, config.useGpuDelegate)
            assertEquals(false, config.gpuFallbackToCpu)
            assertEquals(384, config.embeddingDimension)
        }

        @Test
        @DisplayName("copy preserves unmodified fields")
        fun copyPreservesFields() {
            val original = GemmaModelConfig(modelPath = "/path/model.task")
            val modified = original.copy(temperature = 0.9f)

            assertEquals(original.modelPath, modified.modelPath)
            assertEquals(original.maxTokens, modified.maxTokens)
            assertEquals(0.9f, modified.temperature)
            assertEquals(original.topK, modified.topK)
            assertEquals(original.embeddingDimension, modified.embeddingDimension)
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("rejects blank modelPath")
        fun rejectsBlankModelPath() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "")
            }
        }

        @Test
        @DisplayName("rejects whitespace-only modelPath")
        fun rejectsWhitespaceModelPath() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "   ")
            }
        }

        @Test
        @DisplayName("rejects zero maxTokens")
        fun rejectsZeroMaxTokens() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", maxTokens = 0)
            }
        }

        @Test
        @DisplayName("rejects negative maxTokens")
        fun rejectsNegativeMaxTokens() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", maxTokens = -1)
            }
        }

        @Test
        @DisplayName("rejects negative temperature")
        fun rejectsNegativeTemperature() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", temperature = -0.1f)
            }
        }

        @Test
        @DisplayName("rejects temperature above 2.0")
        fun rejectsHighTemperature() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", temperature = 2.1f)
            }
        }

        @Test
        @DisplayName("accepts boundary temperature values")
        fun acceptsBoundaryTemperatures() {
            val zero = GemmaModelConfig(modelPath = "/path", temperature = 0.0f)
            assertEquals(0.0f, zero.temperature)

            val max = GemmaModelConfig(modelPath = "/path", temperature = 2.0f)
            assertEquals(2.0f, max.temperature)
        }

        @Test
        @DisplayName("rejects zero topK")
        fun rejectsZeroTopK() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", topK = 0)
            }
        }

        @Test
        @DisplayName("rejects unsupported embedding dimension")
        fun rejectsUnsupportedDimension() {
            assertFailsWith<IllegalArgumentException> {
                GemmaModelConfig(modelPath = "/path", embeddingDimension = 100)
            }
        }

        @Test
        @DisplayName("accepts all supported dimensions (256, 384, 512, 768)")
        fun acceptsAllSupportedDimensions() {
            for (dim in GemmaModelConfig.SUPPORTED_DIMENSIONS) {
                val config = GemmaModelConfig(modelPath = "/path", embeddingDimension = dim)
                assertEquals(dim, config.embeddingDimension)
            }
        }
    }

    @Nested
    @DisplayName("Supported dimensions")
    inner class SupportedDimensionTests {

        @Test
        @DisplayName("contains exactly four supported dimensions")
        fun containsFourDimensions() {
            assertEquals(4, GemmaModelConfig.SUPPORTED_DIMENSIONS.size)
        }

        @Test
        @DisplayName("includes 256, 384, 512, 768")
        fun includesExpectedValues() {
            assertTrue(256 in GemmaModelConfig.SUPPORTED_DIMENSIONS)
            assertTrue(384 in GemmaModelConfig.SUPPORTED_DIMENSIONS)
            assertTrue(512 in GemmaModelConfig.SUPPORTED_DIMENSIONS)
            assertTrue(768 in GemmaModelConfig.SUPPORTED_DIMENSIONS)
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    inner class EqualityTests {

        @Test
        @DisplayName("equal configs are equal")
        fun equalConfigsAreEqual() {
            val a = GemmaModelConfig(modelPath = "/path/a.task")
            val b = GemmaModelConfig(modelPath = "/path/a.task")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        @DisplayName("configs with different paths are not equal")
        fun differentPathsNotEqual() {
            val a = GemmaModelConfig(modelPath = "/path/a.task")
            val b = GemmaModelConfig(modelPath = "/path/b.task")
            assertTrue(a != b)
        }
    }
}
