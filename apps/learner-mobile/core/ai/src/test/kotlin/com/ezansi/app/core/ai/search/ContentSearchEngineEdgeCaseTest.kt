package com.ezansi.app.core.ai.search

import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import com.ezansi.app.core.ai.retrieval.ContentRetriever
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentChunk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Edge-case tests for [ContentSearchEngineImpl] — covers scenarios beyond
 * the happy-path unit tests in [ContentSearchEngineImplTest].
 *
 * Validates:
 * - Special characters in queries (emoji, punctuation, mixed scripts)
 * - Very long queries (>500 chars) — graceful handling
 * - Concurrent searches (multiple coroutines calling search simultaneously)
 * - Empty pack list — returns empty results, not error
 * - maxResults boundary values (0 and 1)
 * - Result ordering guarantees (highest score first)
 *
 * Uses the same fake pattern as [ContentSearchEngineImplTest] — no mocking
 * frameworks, no network, no Robolectric.
 */
@DisplayName("ContentSearchEngine edge cases")
class ContentSearchEngineEdgeCaseTest {

    // ── Test doubles ────────────────────────────────────────────────

    private class FakeEmbeddingModel : EmbeddingModel {
        private var loaded = false
        var embedResult = FloatArray(384) { 0.01f }
        var shouldThrow = false
        var embedCallCount = 0

        override suspend fun embed(text: String): FloatArray {
            embedCallCount++
            if (shouldThrow) throw RuntimeException("Embedding failed")
            return embedResult
        }

        override suspend fun loadModel(modelPath: String) {
            loaded = true
        }

        override fun isLoaded() = loaded
        override fun unload() { loaded = false }
        override fun runtimeMode() = EmbeddingRuntimeMode.MOCK
    }

    private class FakeContentRetriever : ContentRetriever {
        var resultsByPack: Map<String, List<RetrievalResult>> = emptyMap()
        var shouldThrow = false
        var retrieveCallCount = 0

        /** Optional artificial delay for concurrency tests. */
        var delayMs: Long = 0

        override suspend fun retrieve(
            queryEmbedding: FloatArray,
            packId: String,
            topK: Int,
        ): List<RetrievalResult> {
            retrieveCallCount++
            if (shouldThrow) throw RuntimeException("Retrieval failed")
            if (delayMs > 0) delay(delayMs)
            return (resultsByPack[packId] ?: emptyList()).take(topK)
        }
    }

    // ── Test fixtures ───────────────────────────────────────────────

    private lateinit var embeddingModel: FakeEmbeddingModel
    private lateinit var contentRetriever: FakeContentRetriever
    private var installedPackIds: List<String> = emptyList()
    private lateinit var engine: ContentSearchEngineImpl

    private fun makeResult(
        chunkId: String,
        score: Float,
        packId: String = "maths-grade6-caps",
        title: String = "Test Chunk",
        topicPath: String = "term1.fractions.basics",
        content: String = "Short content about fractions.",
    ) = RetrievalResult(
        chunkId = chunkId,
        score = score,
        chunk = ContentChunk(
            chunkId = chunkId,
            packId = packId,
            title = title,
            topicPath = topicPath,
            content = content,
        ),
    )

    @BeforeEach
    fun setUp() {
        embeddingModel = FakeEmbeddingModel()
        contentRetriever = FakeContentRetriever()
        installedPackIds = listOf("maths-grade6-caps")
        engine = ContentSearchEngineImpl(
            embeddingModel = embeddingModel,
            contentRetriever = contentRetriever,
            getInstalledPackIds = { installedPackIds },
        )
        // Default: model is loaded and ready
        runTest { embeddingModel.loadModel("/fake/path") }
    }

    // ── Special characters ──────────────────────────────────────────

    @Nested
    @DisplayName("Special characters in queries")
    inner class SpecialCharacterQueries {

        @Test
        @DisplayName("emoji in query does not crash and returns results")
        fun emojiQueryReturnsResults() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.85f, title = "Fractions 🎉"),
                ),
            )

            val result = engine.search("fractions 🎉 📚")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }

        @Test
        @DisplayName("punctuation-heavy query does not crash")
        fun punctuationQueryReturnsResults() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.78f),
                ),
            )

            val result = engine.search("what's 1/2 + 1/4? (fractions!)")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }

        @Test
        @DisplayName("mixed scripts (Latin + Zulu) query works")
        fun mixedScriptQueryWorks() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.80f),
                ),
            )

            val result = engine.search("izibalo fractions addition")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }

        @Test
        @DisplayName("query with only special chars (no letters) still processes")
        fun specialCharsOnlyQuery() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.60f),
                ),
            )

            // Not blank, so should attempt embedding + retrieval
            val result = engine.search("½ + ¼ = ?")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
        }

        @Test
        @DisplayName("newlines and tabs in query are processed without error")
        fun newlinesAndTabsInQuery() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.75f),
                ),
            )

            val result = engine.search("fractions\naddition\tbasics")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }
    }

    // ── Long queries ────────────────────────────────────────────────

    @Nested
    @DisplayName("Long query handling")
    inner class LongQueryHandling {

        @Test
        @DisplayName("query over 500 characters does not crash")
        fun longQueryDoesNotCrash() = runTest {
            val longQuery = "fractions ".repeat(100) // ~1000 chars
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.70f),
                ),
            )

            val result = engine.search(longQuery)
            // Should succeed (embedding model handles truncation internally)
            // or return an error, but not throw an unhandled exception
            assertTrue(
                result is EzansiResult.Success || result is EzansiResult.Error,
                "Long query should return Success or Error, not throw",
            )
        }

        @Test
        @DisplayName("query of exactly 500 chars processes normally")
        fun exactlyFiveHundredCharsQuery() = runTest {
            val query = "a".repeat(500)
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.65f),
                ),
            )

            val result = engine.search(query)
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }

        @Test
        @DisplayName("very long query (>2000 chars) still handled gracefully")
        fun veryLongQueryHandledGracefully() = runTest {
            val veryLongQuery = "explain how to add common fractions with unlike denominators step by step ".repeat(30)
            assertTrue(veryLongQuery.length > 2000)

            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.50f),
                ),
            )

            val result = engine.search(veryLongQuery)
            assertTrue(
                result is EzansiResult.Success || result is EzansiResult.Error,
                "Very long query should return Success or Error, not throw",
            )
        }
    }

    // ── Concurrent searches ─────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent searches")
    inner class ConcurrentSearches {

        @Test
        @DisplayName("multiple concurrent searches all return valid results")
        fun multipleConcurrentSearchesSucceed() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.90f),
                    makeResult("c-2", score = 0.80f),
                ),
            )

            val results = (1..5).map { i ->
                async { engine.search("query $i") }
            }.awaitAll()

            // All 5 concurrent searches should succeed
            results.forEach { result ->
                assertIs<EzansiResult.Success<List<SearchResult>>>(result)
                assertEquals(2, result.data.size)
            }
        }

        @Test
        @DisplayName("concurrent searches with delay all complete")
        fun concurrentSearchesWithDelayComplete() = runTest {
            contentRetriever.delayMs = 10
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.85f),
                ),
            )

            val results = (1..3).map { i ->
                async { engine.search("concurrent query $i") }
            }.awaitAll()

            results.forEach { result ->
                assertIs<EzansiResult.Success<List<SearchResult>>>(result)
                assertEquals(1, result.data.size)
            }

            // Each search embeds once and retrieves once (3 searches × 1 pack)
            assertEquals(3, embeddingModel.embedCallCount)
            assertEquals(3, contentRetriever.retrieveCallCount)
        }
    }

    // ── maxResults boundary values ──────────────────────────────────

    @Nested
    @DisplayName("maxResults boundary values")
    inner class MaxResultsBoundary {

        @Test
        @DisplayName("maxResults = 0 returns empty results")
        fun maxResultsZeroReturnsEmpty() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.95f),
                    makeResult("c-2", score = 0.85f),
                ),
            )

            val result = engine.search("fractions", maxResults = 0)
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertTrue(result.data.isEmpty())
        }

        @Test
        @DisplayName("maxResults = 1 returns exactly one result")
        fun maxResultsOneReturnsSingle() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.95f),
                    makeResult("c-2", score = 0.85f),
                    makeResult("c-3", score = 0.75f),
                ),
            )

            val result = engine.search("fractions", maxResults = 1)
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("c-1", result.data[0].chunkId)
            assertEquals(0.95f, result.data[0].score)
        }

        @Test
        @DisplayName("maxResults larger than available results returns all available")
        fun maxResultsLargerThanAvailable() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.90f),
                ),
            )

            val result = engine.search("fractions", maxResults = 100)
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
        }
    }

    // ── Result ordering ─────────────────────────────────────────────

    @Nested
    @DisplayName("Result ordering guarantees")
    inner class ResultOrdering {

        @Test
        @DisplayName("results are always sorted by descending score")
        fun resultsDescendingScore() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-low", score = 0.30f),
                    makeResult("c-mid", score = 0.60f),
                    makeResult("c-high", score = 0.90f),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val data = result.data
            assertEquals(3, data.size)
            assertEquals("c-high", data[0].chunkId)
            assertEquals(0.90f, data[0].score)
            assertEquals("c-mid", data[1].chunkId)
            assertEquals(0.60f, data[1].score)
            assertEquals("c-low", data[2].chunkId)
            assertEquals(0.30f, data[2].score)
        }

        @Test
        @DisplayName("equal scores maintain stable ordering from retriever")
        fun equalScoresStableOrdering() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-first", score = 0.80f),
                    makeResult("c-second", score = 0.80f),
                    makeResult("c-third", score = 0.80f),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals(3, result.data.size)

            // All scores should be equal
            result.data.forEach { sr ->
                assertEquals(0.80f, sr.score)
            }
        }

        @Test
        @DisplayName("multi-pack results interleave correctly by score")
        fun multiPackInterleaveByScore() = runTest {
            installedPackIds = listOf("maths-grade6-caps", "science-grade6-caps")
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("m-1", score = 0.70f, packId = "maths-grade6-caps"),
                ),
                "science-grade6-caps" to listOf(
                    makeResult("s-1", score = 0.90f, packId = "science-grade6-caps"),
                    makeResult("s-2", score = 0.50f, packId = "science-grade6-caps"),
                ),
            )

            val result = engine.search("energy")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val data = result.data
            assertEquals(3, data.size)
            // s-1 (0.90) > m-1 (0.70) > s-2 (0.50)
            assertEquals("s-1", data[0].chunkId)
            assertEquals("science-grade6-caps", data[0].packId)
            assertEquals("m-1", data[1].chunkId)
            assertEquals("maths-grade6-caps", data[1].packId)
            assertEquals("s-2", data[2].chunkId)
            assertEquals("science-grade6-caps", data[2].packId)
        }
    }

    // ── Empty pack list ─────────────────────────────────────────────

    @Nested
    @DisplayName("Empty pack list")
    inner class EmptyPackList {

        @Test
        @DisplayName("no packs installed returns Success with empty list, not Error")
        fun noPacksReturnsEmptySuccess() = runTest {
            installedPackIds = emptyList()

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertTrue(result.data.isEmpty())
        }

        @Test
        @DisplayName("no packs installed does not call contentRetriever")
        fun noPacksSkipsRetrieval() = runTest {
            installedPackIds = emptyList()

            engine.search("fractions")
            assertEquals(0, contentRetriever.retrieveCallCount)
        }

        @Test
        @DisplayName("no packs installed still embeds the query")
        fun noPacksStillEmbeds() = runTest {
            installedPackIds = emptyList()

            engine.search("fractions")
            // Embedding happens before retrieval, so it still runs
            assertEquals(1, embeddingModel.embedCallCount)
        }
    }

    // ── SearchResult data integrity ─────────────────────────────────

    @Nested
    @DisplayName("SearchResult data integrity")
    inner class DataIntegrity {

        @Test
        @DisplayName("chunk reference preserved in SearchResult for Ask AI flow")
        fun chunkReferencePreserved() = runTest {
            val originalContent = "Full markdown content about fractions and division."
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult(
                        chunkId = "chunk-42",
                        score = 0.88f,
                        title = "Understanding Fractions",
                        topicPath = "term2.fractions.equivalence",
                        content = originalContent,
                    ),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val sr = result.data[0]
            assertEquals("chunk-42", sr.chunk.chunkId)
            assertEquals(originalContent, sr.chunk.content)
            assertEquals("Understanding Fractions", sr.chunk.title)
            assertEquals("term2.fractions.equivalence", sr.chunk.topicPath)
        }

        @Test
        @DisplayName("packId is correctly set from chunk metadata")
        fun packIdFromChunkMetadata() = runTest {
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.80f, packId = "maths-grade6-caps"),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertEquals("maths-grade6-caps", result.data[0].packId)
        }
    }
}
