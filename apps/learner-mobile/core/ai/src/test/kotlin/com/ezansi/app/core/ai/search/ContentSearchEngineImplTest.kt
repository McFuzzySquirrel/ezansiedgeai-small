package com.ezansi.app.core.ai.search

import com.ezansi.app.core.ai.embedding.EmbeddingModel
import com.ezansi.app.core.ai.embedding.EmbeddingRuntimeMode
import com.ezansi.app.core.ai.retrieval.ContentRetriever
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentChunk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [ContentSearchEngineImpl] — the search-without-generation flow.
 *
 * Validates the full search pipeline: query validation → embed → retrieve across
 * packs → merge → re-rank → snippet generation → return. Uses fake implementations
 * of [EmbeddingModel] and [ContentRetriever] for deterministic, fast testing.
 *
 * No Robolectric needed — these tests avoid android.util.Log by running against
 * a build with `unitTests.isReturnDefaultValues = true`.
 */
@DisplayName("ContentSearchEngineImpl")
class ContentSearchEngineImplTest {

    // ── Test doubles ────────────────────────────────────────────────

    private class FakeEmbeddingModel : EmbeddingModel {
        private var loaded = false
        var embedResult = FloatArray(384) { 0.01f }
        var shouldThrow = false

        override suspend fun embed(text: String): FloatArray {
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
        /** Map of packId → results to return for that pack. */
        var resultsByPack: Map<String, List<RetrievalResult>> = emptyMap()
        var shouldThrow = false

        override suspend fun retrieve(
            queryEmbedding: FloatArray,
            packId: String,
            topK: Int,
        ): List<RetrievalResult> {
            if (shouldThrow) throw RuntimeException("Retrieval failed")
            return (resultsByPack[packId] ?: emptyList()).take(topK)
        }
    }

    // ── Test fixtures ───────────────────────────────────────────────

    private lateinit var embeddingModel: FakeEmbeddingModel
    private lateinit var contentRetriever: FakeContentRetriever
    private var installedPackIds: List<String> = emptyList()
    private lateinit var engine: ContentSearchEngineImpl

    private fun makeChunk(
        chunkId: String,
        packId: String = "maths-grade6-caps",
        title: String = "Test Chunk",
        topicPath: String = "term1.fractions.basics",
        content: String = "Short content",
    ) = ContentChunk(
        chunkId = chunkId,
        packId = packId,
        title = title,
        topicPath = topicPath,
        content = content,
    )

    private fun makeResult(
        chunkId: String,
        score: Float,
        packId: String = "maths-grade6-caps",
        title: String = "Test Chunk",
        topicPath: String = "term1.fractions.basics",
        content: String = "Short content",
    ) = RetrievalResult(
        chunkId = chunkId,
        score = score,
        chunk = makeChunk(
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

    // ── Query validation ────────────────────────────────────────────

    @Nested
    @DisplayName("Query validation")
    inner class QueryValidation {

        @Test
        @DisplayName("empty query returns Error")
        fun emptyQueryReturnsError() = runTest {
            val result = engine.search("")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("empty", ignoreCase = true))
        }

        @Test
        @DisplayName("blank query (whitespace only) returns Error")
        fun blankQueryReturnsError() = runTest {
            val result = engine.search("   \t\n  ")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("empty", ignoreCase = true))
        }
    }

    // ── Model readiness ─────────────────────────────────────────────

    @Nested
    @DisplayName("Model readiness")
    inner class ModelReadiness {

        @Test
        @DisplayName("model not loaded returns Error")
        fun modelNotLoadedReturnsError() = runTest {
            embeddingModel.unload()
            val result = engine.search("fractions")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("not available", ignoreCase = true))
        }

        @Test
        @DisplayName("isReady() delegates to embeddingModel.isLoaded()")
        fun isReadyDelegatesToEmbeddingModel() = runTest {
            embeddingModel.loadModel("/fake/path")
            assertTrue(engine.isReady())

            embeddingModel.unload()
            assertFalse(engine.isReady())
        }
    }

    // ── Single pack search ──────────────────────────────────────────

    @Nested
    @DisplayName("Single pack search")
    inner class SinglePackSearch {

        @Test
        @DisplayName("results found returns Success with correct SearchResults")
        fun resultsFoundReturnsSuccess() = runTest {
            val retrievalResults = listOf(
                makeResult("chunk-1", score = 0.92f, title = "Understanding Fractions"),
                makeResult("chunk-2", score = 0.85f, title = "Adding Fractions"),
            )
            contentRetriever.resultsByPack = mapOf("maths-grade6-caps" to retrievalResults)

            val result = engine.search("what are fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val searchResults = result.data
            assertEquals(2, searchResults.size)

            val first = searchResults[0]
            assertEquals("chunk-1", first.chunkId)
            assertEquals("Understanding Fractions", first.title)
            assertEquals(0.92f, first.score)
            assertEquals("maths-grade6-caps", first.packId)
            assertEquals("term1.fractions.basics", first.topicPath)

            val second = searchResults[1]
            assertEquals("chunk-2", second.chunkId)
            assertEquals(0.85f, second.score)
        }

        @Test
        @DisplayName("no results returns Success with empty list")
        fun noResultsReturnsEmptySuccess() = runTest {
            contentRetriever.resultsByPack = mapOf("maths-grade6-caps" to emptyList())

            val result = engine.search("quantum physics")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertTrue(result.data.isEmpty())
        }

        @Test
        @DisplayName("no installed packs returns Success with empty list")
        fun noInstalledPacksReturnsEmptySuccess() = runTest {
            installedPackIds = emptyList()

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)
            assertTrue(result.data.isEmpty())
        }
    }

    // ── Multi-pack search ───────────────────────────────────────────

    @Nested
    @DisplayName("Multi-pack search")
    inner class MultiPackSearch {

        @Test
        @DisplayName("results from multiple packs are merged and re-ranked by score")
        fun mergedAndReRankedByScore() = runTest {
            installedPackIds = listOf("maths-grade6-caps", "science-grade6-caps")

            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("m-1", score = 0.90f, packId = "maths-grade6-caps"),
                    makeResult("m-2", score = 0.70f, packId = "maths-grade6-caps"),
                ),
                "science-grade6-caps" to listOf(
                    makeResult("s-1", score = 0.95f, packId = "science-grade6-caps"),
                    makeResult("s-2", score = 0.80f, packId = "science-grade6-caps"),
                ),
            )

            val result = engine.search("sharing equally")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val data = result.data
            assertEquals(4, data.size)

            // Verify descending score order (re-ranked across packs)
            assertEquals("s-1", data[0].chunkId)
            assertEquals(0.95f, data[0].score)
            assertEquals("science-grade6-caps", data[0].packId)

            assertEquals("m-1", data[1].chunkId)
            assertEquals(0.90f, data[1].score)

            assertEquals("s-2", data[2].chunkId)
            assertEquals(0.80f, data[2].score)

            assertEquals("m-2", data[3].chunkId)
            assertEquals(0.70f, data[3].score)
        }

        @Test
        @DisplayName("results limited to maxResults after merge")
        fun limitedToMaxResults() = runTest {
            installedPackIds = listOf("pack-a", "pack-b")

            contentRetriever.resultsByPack = mapOf(
                "pack-a" to listOf(
                    makeResult("a-1", score = 0.99f, packId = "pack-a"),
                    makeResult("a-2", score = 0.85f, packId = "pack-a"),
                    makeResult("a-3", score = 0.60f, packId = "pack-a"),
                ),
                "pack-b" to listOf(
                    makeResult("b-1", score = 0.92f, packId = "pack-b"),
                    makeResult("b-2", score = 0.78f, packId = "pack-b"),
                    makeResult("b-3", score = 0.55f, packId = "pack-b"),
                ),
            )

            val result = engine.search("fractions", maxResults = 3)
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val data = result.data
            assertEquals(3, data.size)
            assertEquals("a-1", data[0].chunkId) // 0.99
            assertEquals("b-1", data[1].chunkId) // 0.92
            assertEquals("a-2", data[2].chunkId) // 0.85
        }
    }

    // ── Snippet generation ──────────────────────────────────────────

    @Nested
    @DisplayName("Snippet generation")
    inner class SnippetGeneration {

        @Test
        @DisplayName("short content is used as snippet without truncation")
        fun shortContentNotTruncated() = runTest {
            val shortContent = "Fractions represent parts of a whole."
            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.9f, content = shortContent),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            assertEquals(shortContent, result.data[0].snippet)
        }

        @Test
        @DisplayName("long content is truncated at ~100 chars with ellipsis")
        fun longContentTruncatedWithEllipsis() = runTest {
            // 150 chars of content that exceeds the 100-char limit
            val longContent = "A fraction represents a part of a whole number. " +
                "When we divide something into equal parts, each part is a fraction. " +
                "For example, if we cut a pizza into 4 equal slices."

            contentRetriever.resultsByPack = mapOf(
                "maths-grade6-caps" to listOf(
                    makeResult("c-1", score = 0.9f, content = longContent),
                ),
            )

            val result = engine.search("fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val snippet = result.data[0].snippet
            assertTrue(
                snippet.length <= ContentSearchEngineImpl.SNIPPET_MAX_LENGTH + 1,
                "Snippet should be at most ${ContentSearchEngineImpl.SNIPPET_MAX_LENGTH} chars + ellipsis, " +
                    "but was ${snippet.length}: \"$snippet\"",
            )
            assertTrue(snippet.endsWith("…"), "Snippet should end with ellipsis: \"$snippet\"")
        }

        @Test
        @DisplayName("snippet truncation respects word boundaries")
        fun snippetRespectsWordBoundaries() {
            // "abcdefghij" repeated 12 times with spaces = well over 100 chars
            val longContent = (1..12).joinToString(" ") { "abcdefghij" }

            val snippet = generateSnippet(longContent)

            assertTrue(snippet.endsWith("…"))
            // Should not end with a partial word (no mid-word cut)
            val withoutEllipsis = snippet.dropLast(1)
            assertFalse(
                withoutEllipsis.last().isLetter() && withoutEllipsis.length == ContentSearchEngineImpl.SNIPPET_MAX_LENGTH,
                "Snippet should cut at a word boundary, not mid-word",
            )
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("embedding exception returns Error")
        fun embeddingExceptionReturnsError() = runTest {
            embeddingModel.shouldThrow = true

            val result = engine.search("fractions")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("failed", ignoreCase = true))
        }

        @Test
        @DisplayName("retrieval exception returns Error")
        fun retrievalExceptionReturnsError() = runTest {
            contentRetriever.shouldThrow = true

            val result = engine.search("fractions")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("failed", ignoreCase = true))
        }

        @Test
        @DisplayName("error result includes cause for logging")
        fun errorIncludesCause() = runTest {
            embeddingModel.shouldThrow = true

            val result = engine.search("fractions")
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.cause is RuntimeException)
        }
    }

    // ── SearchResult mapping ────────────────────────────────────────

    @Nested
    @DisplayName("SearchResult mapping")
    inner class SearchResultMapping {

        @Test
        @DisplayName("RetrievalResult maps correctly to SearchResult")
        fun retrievalResultMapsToSearchResult() = runTest {
            val chunk = ContentChunk(
                chunkId = "chunk-42",
                packId = "maths-grade6-caps",
                title = "Equivalent Fractions",
                topicPath = "term2.fractions.equivalence",
                content = "Two fractions are equivalent when they represent the same amount.",
                relevanceScore = 0.88f,
                difficulty = "intermediate",
                term = 2,
            )
            val retrieval = RetrievalResult(
                chunkId = "chunk-42",
                score = 0.88f,
                chunk = chunk,
            )
            contentRetriever.resultsByPack = mapOf("maths-grade6-caps" to listOf(retrieval))

            val result = engine.search("equivalent fractions")
            assertIs<EzansiResult.Success<List<SearchResult>>>(result)

            val sr = result.data[0]
            assertEquals("chunk-42", sr.chunkId)
            assertEquals("Equivalent Fractions", sr.title)
            assertEquals("term2.fractions.equivalence", sr.topicPath)
            assertEquals(0.88f, sr.score)
            assertEquals("maths-grade6-caps", sr.packId)
            assertEquals(chunk, sr.chunk) // Full chunk preserved for "Ask AI"
        }
    }

    // ── generateSnippet unit tests ──────────────────────────────────

    @Nested
    @DisplayName("generateSnippet()")
    inner class GenerateSnippetTests {

        @Test
        @DisplayName("empty content returns empty string")
        fun emptyContentReturnsEmpty() {
            assertEquals("", generateSnippet(""))
        }

        @Test
        @DisplayName("whitespace-only content returns empty string")
        fun whitespaceContentReturnsEmpty() {
            assertEquals("", generateSnippet("   \n\t  "))
        }

        @Test
        @DisplayName("content exactly at limit is not truncated")
        fun exactLimitNotTruncated() {
            val content = "a".repeat(ContentSearchEngineImpl.SNIPPET_MAX_LENGTH)
            assertEquals(content, generateSnippet(content))
        }

        @Test
        @DisplayName("content one char over limit is truncated")
        fun oneOverLimitTruncated() {
            val content = "a".repeat(ContentSearchEngineImpl.SNIPPET_MAX_LENGTH + 1)
            val snippet = generateSnippet(content)
            assertTrue(snippet.endsWith("…"))
        }
    }
}
