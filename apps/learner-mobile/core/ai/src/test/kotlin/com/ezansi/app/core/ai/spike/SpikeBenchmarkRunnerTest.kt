package com.ezansi.app.core.ai.spike

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SpikeBenchmarkRunner] — P0-006 benchmark orchestrator.
 *
 * Tests the data collection logic, quality scoring, memory measurement,
 * vector math, JSON serialisation, and results aggregation without
 * requiring a real model or Android device.
 *
 * Uses `testOptions.unitTests.isReturnDefaultValues = true` from build.gradle.kts
 * to handle android.os.Debug calls without Robolectric.
 */
@DisplayName("SpikeBenchmarkRunner")
class SpikeBenchmarkRunnerTest {

    private val defaultConfig = SpikeBenchmarkRunner.BenchmarkConfig(
        modelId = "gemma4-1b-int4",
        modelPath = "/fake/model.task",
        runtime = "mediapipe-genai",
        maxTokens = 150,
        temperature = 0.3f,
        warmupRuns = 1,
        benchmarkRuns = 3,
        useGpuDelegate = true,
        embeddingDimension = 384,
        retrievalTopK = 3,
    )

    private val runner = SpikeBenchmarkRunner(defaultConfig)

    @Nested
    @DisplayName("Quality scoring")
    inner class QualityScoringTests {

        @Test
        @DisplayName("scores all concepts hit at 1.0")
        fun allConceptsHit() {
            val response = "The LCD of 3 and 4 is 12. We get 8/12 + 3/12 = 11/12. " +
                "These are equivalent fractions."
            val concepts = listOf("LCD", "equivalent fractions", "12", "11/12")

            val score = runner.scoreResponse(response, concepts)

            assertEquals(1.0, score.score)
            assertEquals(concepts.size, score.hits.size)
            assertTrue(score.misses.isEmpty())
        }

        @Test
        @DisplayName("scores partial concept hits correctly")
        fun partialConceptHits() {
            val response = "The LCD is 12. You need to find equivalent fractions."
            val concepts = listOf("LCD", "equivalent fractions", "12", "8/12", "3/12", "11/12")

            val score = runner.scoreResponse(response, concepts)

            assertEquals(3, score.hits.size)
            assertEquals(3, score.misses.size)
            assertEquals(0.5, score.score)
        }

        @Test
        @DisplayName("scores zero concepts hit at 0.0")
        fun noConceptsHit() {
            val response = "This is a completely unrelated response about geography."
            val concepts = listOf("LCD", "fractions", "12")

            val score = runner.scoreResponse(response, concepts)

            assertEquals(0.0, score.score)
            assertTrue(score.hits.isEmpty())
            assertEquals(3, score.misses.size)
        }

        @Test
        @DisplayName("handles empty concept list")
        fun emptyConceptList() {
            val score = runner.scoreResponse("Any response", emptyList())
            assertEquals(0.0, score.score)
        }

        @Test
        @DisplayName("scoring is case-insensitive")
        fun caseInsensitiveScoring() {
            val response = "the lcd is 12"
            val concepts = listOf("LCD", "12")

            val score = runner.scoreResponse(response, concepts)

            assertEquals(1.0, score.score)
            assertEquals(2, score.hits.size)
        }
    }

    @Nested
    @DisplayName("Dot product")
    inner class DotProductTests {

        @Test
        @DisplayName("computes dot product of identical normalised vectors as ≈ 1.0")
        fun identicalVectorsDotProduct() {
            val vec = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f) // magnitude = 1.0
            val result = runner.dotProduct(vec, vec)
            assertEquals(1.0f, result, 0.001f)
        }

        @Test
        @DisplayName("computes dot product of orthogonal vectors as 0.0")
        fun orthogonalVectorsDotProduct() {
            val a = floatArrayOf(1.0f, 0.0f, 0.0f)
            val b = floatArrayOf(0.0f, 1.0f, 0.0f)
            val result = runner.dotProduct(a, b)
            assertEquals(0.0f, result)
        }

        @Test
        @DisplayName("rejects mismatched dimensions")
        fun rejectsMismatchedDimensions() {
            val a = floatArrayOf(1.0f, 0.0f)
            val b = floatArrayOf(1.0f, 0.0f, 0.0f)

            assertFailsWith<IllegalArgumentException> {
                runner.dotProduct(a, b)
            }
        }

        @Test
        @DisplayName("handles zero vectors")
        fun handlesZeroVectors() {
            val a = floatArrayOf(0.0f, 0.0f, 0.0f)
            val b = floatArrayOf(0.0f, 0.0f, 0.0f)
            val result = runner.dotProduct(a, b)
            assertEquals(0.0f, result)
        }
    }

    @Nested
    @DisplayName("Results aggregation")
    inner class ResultsAggregationTests {

        @Test
        @DisplayName("aggregates generation results correctly")
        fun aggregatesGenerationResults() {
            val genResults = listOf(
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "test-1",
                    question = "What is 1+1?",
                    avgTimeMs = 3000,
                    minTimeMs = 2500,
                    maxTimeMs = 3500,
                    avgTokens = 50,
                    qualityScore = 0.8,
                    conceptHits = listOf("add", "2"),
                    conceptMisses = emptyList(),
                    peakRamMb = 800f,
                    responsePreview = "The answer is 2",
                ),
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "test-2",
                    question = "What is 2+2?",
                    avgTimeMs = 4000,
                    minTimeMs = 3500,
                    maxTimeMs = 4500,
                    avgTokens = 60,
                    qualityScore = 0.6,
                    conceptHits = listOf("add"),
                    conceptMisses = listOf("4"),
                    peakRamMb = 850f,
                    responsePreview = "Adding numbers",
                ),
            )

            val results = runner.aggregateResults(genResults, emptyList(), engineAvailable = true)

            assertEquals(3500L, results.summary.avgGenerationTimeMs) // (3000+4000)/2
            assertEquals(0.7, results.summary.avgQualityScore) // (0.8+0.6)/2
            assertEquals(850f, results.summary.peakRamMb) // max of 800, 850
            assertEquals(2, results.summary.totalPrompts)
            assertTrue(results.summary.engineAvailable)
        }

        @Test
        @DisplayName("aggregates embedding results correctly")
        fun aggregatesEmbeddingResults() {
            val embedResults = listOf(
                SpikeBenchmarkRunner.EmbeddingResult(
                    queryId = "q1",
                    queryText = "fractions",
                    embedTimeMs = 5,
                    dimension = 384,
                    topKAccuracy = 0.0,
                    retrievedIds = listOf("chunk-1", "chunk-2"),
                ),
                SpikeBenchmarkRunner.EmbeddingResult(
                    queryId = "q2",
                    queryText = "geometry",
                    embedTimeMs = 7,
                    dimension = 384,
                    topKAccuracy = 0.0,
                    retrievedIds = listOf("chunk-3"),
                ),
            )

            val results = runner.aggregateResults(emptyList(), embedResults, engineAvailable = false)

            assertEquals(6L, results.summary.avgEmbedTimeMs) // (5+7)/2
            assertEquals(2, results.summary.totalQueries)
            assertFalse(results.summary.engineAvailable)
        }

        @Test
        @DisplayName("handles empty results gracefully")
        fun handlesEmptyResults() {
            val results = runner.aggregateResults(emptyList(), emptyList(), engineAvailable = false)

            assertEquals(0L, results.summary.avgGenerationTimeMs)
            assertEquals(0.0, results.summary.avgQualityScore)
            assertEquals(0f, results.summary.peakRamMb)
            assertEquals(0, results.summary.totalPrompts)
            assertEquals(0, results.summary.totalQueries)
        }
    }

    @Nested
    @DisplayName("Acceptance criteria")
    inner class AcceptanceCriteriaTests {

        @Test
        @DisplayName("GPU mode uses 5s threshold")
        fun gpuModeThreshold() {
            val gpuRunner = SpikeBenchmarkRunner(defaultConfig.copy(useGpuDelegate = true))
            val genResult = listOf(
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "t1", question = "?",
                    avgTimeMs = 4500, minTimeMs = 4000, maxTimeMs = 5000,
                    avgTokens = 50, qualityScore = 0.8,
                    conceptHits = emptyList(), conceptMisses = emptyList(),
                    peakRamMb = 900f, responsePreview = "test",
                ),
            )

            val results = gpuRunner.aggregateResults(genResult, emptyList(), true)
            assertTrue(results.acceptance.generationTimePass) // 4500 ≤ 5000
            assertTrue(results.acceptance.peakRamPass) // 900 ≤ 1200
        }

        @Test
        @DisplayName("CPU mode uses 10s threshold")
        fun cpuModeThreshold() {
            val cpuRunner = SpikeBenchmarkRunner(defaultConfig.copy(useGpuDelegate = false))
            val genResult = listOf(
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "t1", question = "?",
                    avgTimeMs = 8000, minTimeMs = 7000, maxTimeMs = 9000,
                    avgTokens = 50, qualityScore = 0.8,
                    conceptHits = emptyList(), conceptMisses = emptyList(),
                    peakRamMb = 900f, responsePreview = "test",
                ),
            )

            val results = cpuRunner.aggregateResults(genResult, emptyList(), true)
            assertTrue(results.acceptance.generationTimePass) // 8000 ≤ 10000
        }

        @Test
        @DisplayName("fails generation time threshold when exceeded")
        fun failsGenerationTimeThreshold() {
            val genResult = listOf(
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "t1", question = "?",
                    avgTimeMs = 6000, minTimeMs = 5500, maxTimeMs = 6500,
                    avgTokens = 50, qualityScore = 0.8,
                    conceptHits = emptyList(), conceptMisses = emptyList(),
                    peakRamMb = 900f, responsePreview = "test",
                ),
            )

            val results = runner.aggregateResults(genResult, emptyList(), true)
            assertFalse(results.acceptance.generationTimePass) // 6000 > 5000 (GPU)
        }

        @Test
        @DisplayName("fails RAM threshold when exceeded")
        fun failsRamThreshold() {
            val genResult = listOf(
                SpikeBenchmarkRunner.GenerationResult(
                    promptId = "t1", question = "?",
                    avgTimeMs = 3000, minTimeMs = 2500, maxTimeMs = 3500,
                    avgTokens = 50, qualityScore = 0.8,
                    conceptHits = emptyList(), conceptMisses = emptyList(),
                    peakRamMb = 1500f, responsePreview = "test",
                ),
            )

            val results = runner.aggregateResults(genResult, emptyList(), true)
            assertFalse(results.acceptance.peakRamPass) // 1500 > 1200
        }

        @Test
        @DisplayName("embedding time passes at <100ms")
        fun embedTimePass() {
            val embedResult = listOf(
                SpikeBenchmarkRunner.EmbeddingResult(
                    queryId = "q1", queryText = "test",
                    embedTimeMs = 50, dimension = 384,
                    topKAccuracy = 0.0, retrievedIds = emptyList(),
                ),
            )

            val results = runner.aggregateResults(emptyList(), embedResult, true)
            assertTrue(results.acceptance.embedTimePass) // 50 ≤ 100
        }

        @Test
        @DisplayName("embedding time fails at >100ms")
        fun embedTimeFail() {
            val embedResult = listOf(
                SpikeBenchmarkRunner.EmbeddingResult(
                    queryId = "q1", queryText = "test",
                    embedTimeMs = 150, dimension = 384,
                    topKAccuracy = 0.0, retrievedIds = emptyList(),
                ),
            )

            val results = runner.aggregateResults(emptyList(), embedResult, true)
            assertFalse(results.acceptance.embedTimePass) // 150 > 100
        }
    }

    @Nested
    @DisplayName("JSON serialisation")
    inner class JsonSerialisationTests {

        @Test
        @DisplayName("produces valid JSON with all required fields")
        fun producesValidJson() {
            val results = runner.aggregateResults(
                generationResults = listOf(
                    SpikeBenchmarkRunner.GenerationResult(
                        promptId = "fractions-add",
                        question = "How do I add 2/3 and 1/4?",
                        avgTimeMs = 3000,
                        minTimeMs = 2500,
                        maxTimeMs = 3500,
                        avgTokens = 50,
                        qualityScore = 0.8,
                        conceptHits = listOf("LCD", "12"),
                        conceptMisses = listOf("11/12"),
                        peakRamMb = 800f,
                        responsePreview = "To add fractions...",
                    ),
                ),
                embeddingResults = listOf(
                    SpikeBenchmarkRunner.EmbeddingResult(
                        queryId = "q1",
                        queryText = "fractions",
                        embedTimeMs = 5,
                        dimension = 384,
                        topKAccuracy = 0.0,
                        retrievedIds = listOf("chunk-1"),
                    ),
                ),
                engineAvailable = false,
            )

            val json = runner.toJson(results)

            // Verify it's parseable JSON
            val parsed = org.json.JSONObject(json)

            // Check top-level fields
            assertEquals("gemma4-1b-int4", parsed.getString("model"))
            assertEquals("mediapipe-genai", parsed.getString("runtime"))
            assertTrue(parsed.getBoolean("gpu_enabled"))

            // Check benchmarks array
            val benchmarks = parsed.getJSONArray("benchmarks")
            assertEquals(1, benchmarks.length())
            assertEquals("fractions-add", benchmarks.getJSONObject(0).getString("prompt_id"))
            assertEquals(3.0, benchmarks.getJSONObject(0).getDouble("avg_time_s"), 0.01)

            // Check embedding benchmarks
            val embedBenchmarks = parsed.getJSONArray("embedding_benchmarks")
            assertEquals(1, embedBenchmarks.length())
            assertEquals("q1", embedBenchmarks.getJSONObject(0).getString("query_id"))

            // Check summary
            val summary = parsed.getJSONObject("summary")
            assertEquals(3.0, summary.getDouble("avg_generation_time_s"), 0.01)
            assertFalse(summary.getBoolean("engine_available"))

            // Check acceptance
            val acceptance = parsed.getJSONObject("acceptance")
            assertTrue(acceptance.has("generation_time_pass"))
            assertTrue(acceptance.has("peak_ram_pass"))
            assertTrue(acceptance.has("quality_pass"))
            assertTrue(acceptance.has("embed_time_pass"))
        }

        @Test
        @DisplayName("handles empty results in JSON output")
        fun handlesEmptyResultsJson() {
            val results = runner.aggregateResults(emptyList(), emptyList(), false)
            val json = runner.toJson(results)

            val parsed = org.json.JSONObject(json)
            assertEquals(0, parsed.getJSONArray("benchmarks").length())
            assertEquals(0, parsed.getJSONArray("embedding_benchmarks").length())
        }
    }

    @Nested
    @DisplayName("Median calculation")
    inner class MedianTests {

        @Test
        @DisplayName("computes median of odd-length list")
        fun medianOddLength() {
            assertEquals(3L, listOf(1L, 3L, 5L).median())
        }

        @Test
        @DisplayName("computes median of even-length list")
        fun medianEvenLength() {
            assertEquals(3L, listOf(1L, 2L, 4L, 5L).median())
        }

        @Test
        @DisplayName("computes median of single element")
        fun medianSingleElement() {
            assertEquals(42L, listOf(42L).median())
        }

        @Test
        @DisplayName("returns 0 for empty list")
        fun medianEmptyList() {
            assertEquals(0L, emptyList<Long>().median())
        }

        @Test
        @DisplayName("handles unsorted input")
        fun medianUnsorted() {
            assertEquals(3L, listOf(5L, 1L, 3L).median())
        }
    }

    @Nested
    @DisplayName("BenchmarkConfig defaults")
    inner class ConfigDefaultsTests {

        @Test
        @DisplayName("default config matches P0-006 config.yaml values")
        fun defaultConfigMatchesYaml() {
            val config = SpikeBenchmarkRunner.BenchmarkConfig()
            assertEquals("gemma4-1b-int4", config.modelId)
            assertEquals("mediapipe-genai", config.runtime)
            assertEquals(150, config.maxTokens)
            assertEquals(0.3f, config.temperature)
            assertEquals(1, config.warmupRuns)
            assertEquals(3, config.benchmarkRuns)
            assertTrue(config.useGpuDelegate)
            assertEquals(384, config.embeddingDimension)
            assertEquals(3, config.retrievalTopK)
        }
    }

    @Nested
    @DisplayName("TestPrompt data class")
    inner class TestPromptTests {

        @Test
        @DisplayName("creates test prompt with all fields")
        fun createsTestPrompt() {
            val prompt = SpikeBenchmarkRunner.TestPrompt(
                id = "fractions-add",
                content = "To add fractions...",
                question = "How do I add 2/3 and 1/4?",
                expectedConcepts = listOf("LCD", "12"),
            )
            assertEquals("fractions-add", prompt.id)
            assertEquals(2, prompt.expectedConcepts.size)
        }

        @Test
        @DisplayName("expected concepts default to empty list")
        fun defaultEmptyConcepts() {
            val prompt = SpikeBenchmarkRunner.TestPrompt(
                id = "test",
                content = "content",
                question = "question",
            )
            assertTrue(prompt.expectedConcepts.isEmpty())
        }
    }
}
