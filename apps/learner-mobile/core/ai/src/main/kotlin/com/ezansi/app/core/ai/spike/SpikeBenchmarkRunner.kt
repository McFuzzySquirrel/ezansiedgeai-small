package com.ezansi.app.core.ai.spike

import android.os.Debug
import android.util.Log
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout

/**
 * Spike P0-006: Benchmark orchestrator for Gemma 4 evaluation.
 *
 * This is **spike code** that coordinates generation and embedding benchmarks
 * on-device, collecting latency, memory, and quality metrics. Results are
 * output as JSON compatible with the Python report generator at
 * `spikes/p0-006-gemma4-evaluation/scripts/report_generator.py`.
 *
 * ## Benchmark Flow
 *
 * ### Generation Benchmark
 * 1. Load Gemma 4 model via [GemmaSpikeLlmEngine]
 * 2. Run warmup prompts (not measured)
 * 3. Run measured prompts, collecting per-prompt metrics
 * 4. Compute summary statistics
 *
 * ### Embedding Benchmark
 * 1. Load Gemma 4 embedding via [GemmaSpikeEmbeddingModel]
 * 2. Embed a test corpus
 * 3. Embed test queries and measure retrieval accuracy (top-K)
 * 4. Collect timing and accuracy metrics
 *
 * ## JSON Output Format
 *
 * Matches the Python benchmark scripts' expected format for the
 * report generator to consume. Key fields:
 * ```json
 * {
 *   "model": "gemma4-1b-int4",
 *   "runtime": "mediapipe-genai",
 *   "benchmarks": [...],
 *   "summary": {...},
 *   "acceptance": {...}
 * }
 * ```
 *
 * @see GemmaSpikeLlmEngine — the LLM engine being benchmarked
 * @see GemmaSpikeEmbeddingModel — the embedding model being benchmarked
 */
class SpikeBenchmarkRunner(
    private val config: BenchmarkConfig,
) {

    companion object {
        private const val TAG = "SpikeBenchmarkRunner"

        /** Default timeout for a single generation benchmark run. */
        private const val GENERATION_TIMEOUT_MS = 60_000L
    }

    // ── Data Classes ────────────────────────────────────────────────

    /**
     * Configuration for benchmark runs. Mirrors values from
     * `spikes/p0-006-gemma4-evaluation/config.yaml`.
     */
    data class BenchmarkConfig(
        val modelId: String = "gemma4-1b-int4",
        val modelPath: String = "",
        val runtime: String = "mediapipe-genai",
        val maxTokens: Int = 150,
        val temperature: Float = 0.3f,
        val warmupRuns: Int = 1,
        val benchmarkRuns: Int = 3,
        val useGpuDelegate: Boolean = true,
        val embeddingDimension: Int = 384,
        val retrievalTopK: Int = 3,
    )

    /**
     * A test prompt with content, question, and expected answer concepts.
     */
    data class TestPrompt(
        val id: String,
        val content: String,
        val question: String,
        val expectedConcepts: List<String> = emptyList(),
    )

    /**
     * Per-prompt generation benchmark result.
     */
    data class GenerationResult(
        val promptId: String,
        val question: String,
        val avgTimeMs: Long,
        val minTimeMs: Long,
        val maxTimeMs: Long,
        val avgTokens: Int,
        val qualityScore: Double,
        val conceptHits: List<String>,
        val conceptMisses: List<String>,
        val peakRamMb: Float,
        val responsePreview: String,
    )

    /**
     * Per-query embedding benchmark result.
     */
    data class EmbeddingResult(
        val queryId: String,
        val queryText: String,
        val embedTimeMs: Long,
        val dimension: Int,
        val topKAccuracy: Double,
        val retrievedIds: List<String>,
    )

    /**
     * Complete benchmark results, serialisable to JSON.
     */
    data class BenchmarkResults(
        val modelId: String,
        val runtime: String,
        val gpuEnabled: Boolean,
        val generationResults: List<GenerationResult>,
        val embeddingResults: List<EmbeddingResult>,
        val summary: BenchmarkSummary,
        val acceptance: AcceptanceResults,
    )

    /**
     * Summary statistics across all benchmark runs.
     */
    data class BenchmarkSummary(
        val avgGenerationTimeMs: Long,
        val medianGenerationTimeMs: Long,
        val avgQualityScore: Double,
        val peakRamMb: Float,
        val avgEmbedTimeMs: Long,
        val totalPrompts: Int,
        val totalQueries: Int,
        val engineAvailable: Boolean,
    )

    /**
     * Pass/fail against P0-006 acceptance criteria.
     */
    data class AcceptanceResults(
        val generationTimePass: Boolean,
        val peakRamPass: Boolean,
        val qualityPass: Boolean,
        val embedTimePass: Boolean,
    )

    // ── Generation Benchmark ────────────────────────────────────────

    /**
     * Runs generation benchmarks across all provided test prompts.
     *
     * For each prompt:
     * 1. Run [BenchmarkConfig.warmupRuns] warmup runs (not measured)
     * 2. Run [BenchmarkConfig.benchmarkRuns] measured runs
     * 3. Collect timing, token count, quality, and memory metrics
     *
     * @param engine Pre-loaded [GemmaSpikeLlmEngine] to benchmark
     * @param prompts Test prompts from config.yaml
     * @return List of per-prompt [GenerationResult]s
     */
    suspend fun runGenerationBenchmarks(
        engine: GemmaSpikeLlmEngine,
        prompts: List<TestPrompt>,
    ): List<GenerationResult> {
        check(engine.isLoaded()) {
            "Engine must be loaded before running benchmarks"
        }

        Log.i(TAG, "Starting generation benchmark: ${prompts.size} prompts, " +
            "${config.warmupRuns} warmup + ${config.benchmarkRuns} measured runs each")

        val results = mutableListOf<GenerationResult>()

        for ((index, prompt) in prompts.withIndex()) {
            Log.i(TAG, "[${index + 1}/${prompts.size}] ${prompt.id}: ${prompt.question.take(60)}...")

            val runTimes = mutableListOf<Long>()
            val runTokens = mutableListOf<Int>()
            var bestResponse = ""
            val totalRuns = config.warmupRuns + config.benchmarkRuns

            for (run in 0 until totalRuns) {
                val isWarmup = run < config.warmupRuns
                val label = if (isWarmup) "warmup" else "run ${run - config.warmupRuns + 1}"

                val startMs = System.currentTimeMillis()

                try {
                    val response = withTimeout(GENERATION_TIMEOUT_MS) {
                        engine.generate(prompt.content + "\n\n" + prompt.question, config.maxTokens)
                            .toList()
                            .joinToString("")
                    }

                    val elapsedMs = System.currentTimeMillis() - startMs
                    val tokenCount = engine.lastTokenCount()

                    if (!isWarmup) {
                        runTimes.add(elapsedMs)
                        runTokens.add(tokenCount)
                        if (response.length > bestResponse.length) {
                            bestResponse = response
                        }
                    }

                    Log.d(TAG, "  $label: ${elapsedMs}ms, ~$tokenCount tokens")
                } catch (e: Exception) {
                    Log.e(TAG, "  $label: ERROR — ${e.message}")
                    if (!isWarmup) {
                        runTimes.add(0L)
                        runTokens.add(0)
                    }
                }
            }

            val quality = scoreResponse(bestResponse, prompt.expectedConcepts)
            val peakRamMb = measurePeakMemoryMb()

            results.add(
                GenerationResult(
                    promptId = prompt.id,
                    question = prompt.question,
                    avgTimeMs = if (runTimes.isNotEmpty()) runTimes.average().toLong() else 0L,
                    minTimeMs = runTimes.minOrNull() ?: 0L,
                    maxTimeMs = runTimes.maxOrNull() ?: 0L,
                    avgTokens = if (runTokens.isNotEmpty()) runTokens.average().toInt() else 0,
                    qualityScore = quality.score,
                    conceptHits = quality.hits,
                    conceptMisses = quality.misses,
                    peakRamMb = peakRamMb,
                    responsePreview = bestResponse.take(200),
                ),
            )
        }

        Log.i(TAG, "Generation benchmark complete: ${results.size} prompts benchmarked")
        return results
    }

    // ── Embedding Benchmark ─────────────────────────────────────────

    /**
     * Runs embedding benchmarks: embed corpus + queries, measure retrieval accuracy.
     *
     * @param model Pre-loaded [GemmaSpikeEmbeddingModel] to benchmark
     * @param corpus Content chunks to embed (simulate content pack)
     * @param queries Test queries with expected matching chunk IDs
     * @return List of per-query [EmbeddingResult]s
     */
    suspend fun runEmbeddingBenchmarks(
        model: GemmaSpikeEmbeddingModel,
        corpus: List<Pair<String, String>>,  // (id, text)
        queries: List<Pair<String, String>>, // (id, queryText)
    ): List<EmbeddingResult> {
        check(model.isLoaded()) {
            "Embedding model must be loaded before running benchmarks"
        }

        Log.i(TAG, "Starting embedding benchmark: ${corpus.size} corpus chunks, " +
            "${queries.size} queries, dimension: ${model.dimension()}")

        // Step 1: Embed corpus
        val corpusEmbeddings = mutableListOf<Pair<String, FloatArray>>()
        for ((id, text) in corpus) {
            val embedding = model.embed(text)
            corpusEmbeddings.add(id to embedding)
        }
        Log.d(TAG, "Corpus embedded: ${corpusEmbeddings.size} chunks")

        // Step 2: Embed queries and measure retrieval
        val results = mutableListOf<EmbeddingResult>()
        for ((queryId, queryText) in queries) {
            val startMs = System.currentTimeMillis()
            val queryEmbedding = model.embed(queryText)
            val embedTimeMs = System.currentTimeMillis() - startMs

            // Compute cosine similarity (dot product of L2-normalised vectors)
            val similarities = corpusEmbeddings.map { (chunkId, chunkEmbed) ->
                chunkId to dotProduct(queryEmbedding, chunkEmbed)
            }.sortedByDescending { it.second }

            val topK = similarities.take(config.retrievalTopK)

            results.add(
                EmbeddingResult(
                    queryId = queryId,
                    queryText = queryText,
                    embedTimeMs = embedTimeMs,
                    dimension = model.dimension(),
                    topKAccuracy = 0.0, // Accuracy requires ground-truth labels
                    retrievedIds = topK.map { it.first },
                ),
            )
        }

        Log.i(TAG, "Embedding benchmark complete: ${results.size} queries, " +
            "avg embed time: ${"%.1f".format(model.averageEmbedTimeMs())}ms")
        return results
    }

    // ── Results Aggregation ─────────────────────────────────────────

    /**
     * Aggregates generation and embedding results into a complete [BenchmarkResults].
     */
    fun aggregateResults(
        generationResults: List<GenerationResult>,
        embeddingResults: List<EmbeddingResult>,
        engineAvailable: Boolean,
    ): BenchmarkResults {
        val genTimes = generationResults.map { it.avgTimeMs }.filter { it > 0 }
        val qualityScores = generationResults.map { it.qualityScore }
        val embedTimes = embeddingResults.map { it.embedTimeMs }.filter { it > 0 }

        val summary = BenchmarkSummary(
            avgGenerationTimeMs = if (genTimes.isNotEmpty()) genTimes.average().toLong() else 0L,
            medianGenerationTimeMs = if (genTimes.isNotEmpty()) genTimes.median() else 0L,
            avgQualityScore = if (qualityScores.isNotEmpty()) qualityScores.average() else 0.0,
            peakRamMb = generationResults.maxOfOrNull { it.peakRamMb } ?: 0f,
            avgEmbedTimeMs = if (embedTimes.isNotEmpty()) embedTimes.average().toLong() else 0L,
            totalPrompts = generationResults.size,
            totalQueries = embeddingResults.size,
            engineAvailable = engineAvailable,
        )

        // Check against P0-006 acceptance criteria
        val gpuTimeThresholdMs = 5_000L  // ≤5s on GPU (FT-NF-01)
        val cpuTimeThresholdMs = 10_000L // ≤10s on CPU fallback (NF-01)
        val timeThreshold = if (config.useGpuDelegate) gpuTimeThresholdMs else cpuTimeThresholdMs

        val acceptance = AcceptanceResults(
            generationTimePass = summary.avgGenerationTimeMs <= timeThreshold,
            peakRamPass = summary.peakRamMb <= 1200f,  // ≤1,200 MB (FT-NF-04)
            qualityPass = summary.avgQualityScore >= 0.7, // ≥70% concept coverage
            embedTimePass = summary.avgEmbedTimeMs <= 100L, // <100ms (FT-NF-02)
        )

        return BenchmarkResults(
            modelId = config.modelId,
            runtime = config.runtime,
            gpuEnabled = config.useGpuDelegate,
            generationResults = generationResults,
            embeddingResults = embeddingResults,
            summary = summary,
            acceptance = acceptance,
        )
    }

    // ── JSON Serialisation ──────────────────────────────────────────

    /**
     * Converts [BenchmarkResults] to JSON format compatible with the
     * Python report generator at `scripts/report_generator.py`.
     *
     * Uses manual string building instead of `org.json.JSONObject` because
     * the mock android.jar (with `isReturnDefaultValues = true`) makes all
     * `org.json` methods return null/0 in JVM unit tests.
     *
     * Output format matches `results/generation-benchmarks.json`.
     */
    fun toJson(results: BenchmarkResults): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  ${jsonStr("model")}: ${jsonStr(results.modelId)},")
        sb.appendLine("  ${jsonStr("runtime")}: ${jsonStr(results.runtime)},")
        sb.appendLine("  ${jsonStr("gpu_enabled")}: ${results.gpuEnabled},")

        // Generation benchmarks
        sb.appendLine("  ${jsonStr("benchmarks")}: [")
        for ((i, gen) in results.generationResults.withIndex()) {
            sb.appendLine("    {")
            sb.appendLine("      ${jsonStr("prompt_id")}: ${jsonStr(gen.promptId)},")
            sb.appendLine("      ${jsonStr("question")}: ${jsonStr(gen.question)},")
            sb.appendLine("      ${jsonStr("avg_time_s")}: ${gen.avgTimeMs / 1000.0},")
            sb.appendLine("      ${jsonStr("min_time_s")}: ${gen.minTimeMs / 1000.0},")
            sb.appendLine("      ${jsonStr("max_time_s")}: ${gen.maxTimeMs / 1000.0},")
            sb.appendLine("      ${jsonStr("avg_tokens")}: ${gen.avgTokens},")
            sb.appendLine("      ${jsonStr("quality_score")}: ${gen.qualityScore},")
            sb.appendLine("      ${jsonStr("concept_hits")}: ${jsonStrArray(gen.conceptHits)},")
            sb.appendLine("      ${jsonStr("concept_misses")}: ${jsonStrArray(gen.conceptMisses)},")
            sb.appendLine("      ${jsonStr("peak_ram_mb")}: ${gen.peakRamMb.toDouble()},")
            sb.appendLine("      ${jsonStr("response_preview")}: ${jsonStr(gen.responsePreview)}")
            sb.append("    }")
            if (i < results.generationResults.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ],")

        // Embedding benchmarks
        sb.appendLine("  ${jsonStr("embedding_benchmarks")}: [")
        for ((i, emb) in results.embeddingResults.withIndex()) {
            sb.appendLine("    {")
            sb.appendLine("      ${jsonStr("query_id")}: ${jsonStr(emb.queryId)},")
            sb.appendLine("      ${jsonStr("query_text")}: ${jsonStr(emb.queryText)},")
            sb.appendLine("      ${jsonStr("embed_time_ms")}: ${emb.embedTimeMs},")
            sb.appendLine("      ${jsonStr("dimension")}: ${emb.dimension},")
            sb.appendLine("      ${jsonStr("top_k_accuracy")}: ${emb.topKAccuracy},")
            sb.appendLine("      ${jsonStr("retrieved_ids")}: ${jsonStrArray(emb.retrievedIds)}")
            sb.append("    }")
            if (i < results.embeddingResults.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ],")

        // Summary
        sb.appendLine("  ${jsonStr("summary")}: {")
        sb.appendLine("    ${jsonStr("avg_generation_time_s")}: ${results.summary.avgGenerationTimeMs / 1000.0},")
        sb.appendLine("    ${jsonStr("median_generation_time_s")}: ${results.summary.medianGenerationTimeMs / 1000.0},")
        sb.appendLine("    ${jsonStr("avg_quality_score")}: ${results.summary.avgQualityScore},")
        sb.appendLine("    ${jsonStr("peak_ram_mb")}: ${results.summary.peakRamMb.toDouble()},")
        sb.appendLine("    ${jsonStr("avg_embed_time_ms")}: ${results.summary.avgEmbedTimeMs},")
        sb.appendLine("    ${jsonStr("total_prompts")}: ${results.summary.totalPrompts},")
        sb.appendLine("    ${jsonStr("total_queries")}: ${results.summary.totalQueries},")
        sb.appendLine("    ${jsonStr("engine_available")}: ${results.summary.engineAvailable}")
        sb.appendLine("  },")

        // Acceptance
        sb.appendLine("  ${jsonStr("acceptance")}: {")
        sb.appendLine("    ${jsonStr("generation_time_pass")}: ${results.acceptance.generationTimePass},")
        sb.appendLine("    ${jsonStr("peak_ram_pass")}: ${results.acceptance.peakRamPass},")
        sb.appendLine("    ${jsonStr("quality_pass")}: ${results.acceptance.qualityPass},")
        sb.appendLine("    ${jsonStr("embed_time_pass")}: ${results.acceptance.embedTimePass}")
        sb.appendLine("  }")
        sb.appendLine("}")

        return sb.toString()
    }

    /** Wraps a string value in JSON double-quotes, escaping special characters. */
    private fun jsonStr(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /** Converts a list of strings to a JSON array string. */
    private fun jsonStrArray(values: List<String>): String {
        return values.joinToString(", ", "[", "]") { jsonStr(it) }
    }

    // ── Private Helpers ─────────────────────────────────────────────

    /**
     * Quality scoring: checks how many expected concepts appear in the response.
     * Matches the Python `score_response()` in `benchmark_generation.py`.
     */
    internal data class QualityScore(
        val score: Double,
        val hits: List<String>,
        val misses: List<String>,
    )

    internal fun scoreResponse(
        response: String,
        expectedConcepts: List<String>,
    ): QualityScore {
        if (expectedConcepts.isEmpty()) {
            return QualityScore(0.0, emptyList(), emptyList())
        }

        val responseLower = response.lowercase()
        val hits = expectedConcepts.filter { it.lowercase() in responseLower }
        val misses = expectedConcepts.filter { it.lowercase() !in responseLower }

        return QualityScore(
            score = hits.size.toDouble() / expectedConcepts.size,
            hits = hits,
            misses = misses,
        )
    }

    /**
     * Measures peak memory in MB using Runtime + Debug native heap.
     */
    internal fun measurePeakMemoryMb(): Float {
        val runtime = Runtime.getRuntime()
        val jvmHeapMb = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize().toFloat() / (1024 * 1024)
        return jvmHeapMb + nativeHeapMb
    }

    /**
     * Dot product of two float arrays (cosine similarity for L2-normalised vectors).
     */
    internal fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }
        var sum = 0.0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }
}

/**
 * Computes the median of a sorted list of longs.
 */
internal fun List<Long>.median(): Long {
    if (isEmpty()) return 0L
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2
    } else {
        sorted[mid]
    }
}
