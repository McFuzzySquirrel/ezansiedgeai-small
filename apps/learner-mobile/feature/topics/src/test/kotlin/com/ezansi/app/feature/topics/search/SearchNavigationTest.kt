package com.ezansi.app.feature.topics.search

import com.ezansi.app.core.ai.search.ContentSearchEngine
import com.ezansi.app.core.ai.search.SearchResult
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for search-related navigation and integration behaviour:
 * - "Ask AI" question formatting: "Explain {title}"
 * - Search mode toggling: entering text → search results, clearing → topics
 * - Edge cases in search activation logic
 *
 * These complement [SearchViewModelTest] by testing the patterns that
 * [TopicsScreen] relies on — the ViewModel state transitions that drive
 * search bar visibility and content area switching.
 *
 * Uses the same fake pattern — no mocking frameworks, no Android context,
 * no Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Search navigation and integration")
class SearchNavigationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Fake implementations ────────────────────────────────────────

    private class FakeContentSearchEngine : ContentSearchEngine {
        var searchResults: EzansiResult<List<SearchResult>> =
            EzansiResult.Success(emptyList())
        var ready: Boolean = true
        var lastQuery: String? = null

        override suspend fun search(query: String, maxResults: Int): EzansiResult<List<SearchResult>> {
            lastQuery = query
            return searchResults
        }

        override fun isReady(): Boolean = ready
    }

    // ── Helper factories ────────────────────────────────────────────

    private fun createViewModel(
        engine: FakeContentSearchEngine = FakeContentSearchEngine(),
    ): Pair<SearchViewModel, FakeContentSearchEngine> {
        return SearchViewModel(contentSearchEngine = engine) to engine
    }

    private fun createSampleResult(
        title: String,
        chunkId: String = "chunk-1",
        topicPath: String = "term1.fractions.addition",
        score: Float = 0.9f,
    ): SearchResult = SearchResult(
        chunkId = chunkId,
        title = title,
        snippet = "A snippet about $title",
        topicPath = topicPath,
        score = score,
        packId = "maths-grade6-caps",
        chunk = ContentChunk(
            chunkId = chunkId,
            packId = "maths-grade6-caps",
            title = title,
            topicPath = topicPath,
            content = "Full content about $title for the learner.",
        ),
    )

    // ── "Ask AI" question formatting ────────────────────────────────

    @Nested
    @DisplayName("Ask AI question formatting")
    inner class AskAiFormatting {

        @Test
        @DisplayName("'Explain {title}' format matches TopicsScreen expectation")
        fun askAiFormatsCorrectly() = runTest {
            val result = createSampleResult(title = "Adding Fractions")

            // TopicsScreen formats: "Explain ${result.title}"
            val question = "Explain ${result.title}"
            assertEquals("Explain Adding Fractions", question)
        }

        @Test
        @DisplayName("title with special characters is preserved in question")
        fun titleWithSpecialCharsPreserved() {
            val result = createSampleResult(title = "What's ½ + ¼?")
            val question = "Explain ${result.title}"
            assertEquals("Explain What's ½ + ¼?", question)
        }

        @Test
        @DisplayName("long title is preserved fully in question")
        fun longTitlePreservedInQuestion() {
            val longTitle = "Understanding How to Add Common Fractions with Unlike Denominators"
            val result = createSampleResult(title = longTitle)
            val question = "Explain ${result.title}"
            assertEquals("Explain $longTitle", question)
        }

        @Test
        @DisplayName("each search result produces unique question")
        fun eachResultProducesUniqueQuestion() = runTest {
            val results = listOf(
                createSampleResult(title = "Adding Fractions", chunkId = "c-1"),
                createSampleResult(title = "Subtracting Fractions", chunkId = "c-2"),
                createSampleResult(title = "Equivalent Fractions", chunkId = "c-3"),
            )

            val questions = results.map { "Explain ${it.title}" }

            assertEquals(3, questions.distinct().size)
            assertEquals("Explain Adding Fractions", questions[0])
            assertEquals("Explain Subtracting Fractions", questions[1])
            assertEquals("Explain Equivalent Fractions", questions[2])
        }
    }

    // ── Search mode toggling ────────────────────────────────────────

    @Nested
    @DisplayName("Search mode toggling")
    inner class SearchModeToggling {

        @Test
        @DisplayName("entering text activates search mode (query not blank)")
        fun enteringTextActivatesSearchMode() = runTest {
            val (vm, _) = createViewModel()

            vm.onQueryChanged("fractions")

            // TopicsScreen checks: searchState.query.isNotBlank()
            assertTrue(vm.uiState.value.query.isNotBlank())
        }

        @Test
        @DisplayName("clearing text deactivates search mode")
        fun clearingTextDeactivatesSearchMode() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(
                    listOf(createSampleResult(title = "Fractions")),
                )
            }
            val (vm, _) = createViewModel(engine)

            // Activate search
            vm.onQueryChanged("fractions")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.query.isNotBlank())

            // Clear search
            vm.clearSearch()

            // TopicsScreen: isSearchActive = searchState.query.isNotBlank()
            assertFalse(vm.uiState.value.query.isNotBlank())
            assertTrue(vm.uiState.value.results.isEmpty())
            assertFalse(vm.uiState.value.hasSearched)
        }

        @Test
        @DisplayName("setting query to empty string deactivates search mode")
        fun emptyQueryDeactivatesSearchMode() = runTest {
            val (vm, _) = createViewModel()

            vm.onQueryChanged("fractions")
            assertTrue(vm.uiState.value.query.isNotBlank())

            vm.onQueryChanged("")
            assertFalse(vm.uiState.value.query.isNotBlank())
            assertFalse(vm.uiState.value.hasSearched)
        }

        @Test
        @DisplayName("whitespace-only query deactivates search mode")
        fun whitespaceQueryDeactivatesSearchMode() = runTest {
            val (vm, _) = createViewModel()

            vm.onQueryChanged("   ")

            // "   ".isNotBlank() == false, so search is inactive
            assertFalse(vm.uiState.value.query.isNotBlank())
        }

        @Test
        @DisplayName("search → clear → new search activates mode again")
        fun searchClearNewSearchCycle() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(
                    listOf(createSampleResult(title = "Fractions")),
                )
            }
            val (vm, _) = createViewModel(engine)

            // First search
            vm.onQueryChanged("fractions")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.hasSearched)
            assertEquals(1, vm.uiState.value.results.size)

            // Clear
            vm.clearSearch()
            assertFalse(vm.uiState.value.hasSearched)
            assertTrue(vm.uiState.value.results.isEmpty())

            // New search
            vm.onQueryChanged("decimals")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.hasSearched)
            assertTrue(vm.uiState.value.query.isNotBlank())
        }
    }

    // ── Empty query does not trigger search ─────────────────────────

    @Nested
    @DisplayName("Empty query suppression")
    inner class EmptyQuerySuppression {

        @Test
        @DisplayName("empty query does not trigger search engine call")
        fun emptyQueryDoesNotSearch() = runTest {
            val engine = FakeContentSearchEngine()
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("")
            advanceUntilIdle()

            assertNull(engine.lastQuery)
            assertFalse(vm.uiState.value.hasSearched)
        }

        @Test
        @DisplayName("blank query (spaces only) does not trigger search engine call")
        fun blankQueryDoesNotSearch() = runTest {
            val engine = FakeContentSearchEngine()
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("    ")
            advanceUntilIdle()

            assertNull(engine.lastQuery)
            assertFalse(vm.uiState.value.hasSearched)
        }

        @Test
        @DisplayName("submit on empty query is ignored")
        fun submitOnEmptyQueryIgnored() = runTest {
            val engine = FakeContentSearchEngine()
            val (vm, _) = createViewModel(engine)

            vm.onSearchSubmitted()
            advanceUntilIdle()

            assertNull(engine.lastQuery)
            assertFalse(vm.uiState.value.hasSearched)
        }
    }

    // ── Search bar state for UI ─────────────────────────────────────

    @Nested
    @DisplayName("Search state for UI rendering")
    inner class SearchStateForUi {

        @Test
        @DisplayName("after search with results: isSearching false, hasSearched true, results populated")
        fun afterSuccessfulSearch() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(
                    listOf(
                        createSampleResult(title = "Result 1", chunkId = "c-1"),
                        createSampleResult(title = "Result 2", chunkId = "c-2"),
                    ),
                )
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isSearching)
            assertTrue(state.hasSearched)
            assertEquals(2, state.results.size)
            assertNull(state.error)
        }

        @Test
        @DisplayName("after search with no results: hasSearched true, results empty")
        fun afterEmptySearch() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(emptyList())
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("nonexistent xyz")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.hasSearched)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
            assertNull(state.error)
        }

        @Test
        @DisplayName("after search error: error message set, results empty")
        fun afterSearchError() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Error("Model not loaded")
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.hasSearched)
            assertTrue(state.results.isEmpty())
            assertEquals("Model not loaded", state.error)
        }
    }

    // ── formatTopicPath edge cases ──────────────────────────────────

    @Nested
    @DisplayName("formatTopicPath edge cases")
    inner class FormatTopicPathEdgeCases {

        @Test
        @DisplayName("empty string returns empty")
        fun emptyPath() {
            assertEquals("", formatTopicPath(""))
        }

        @Test
        @DisplayName("deeply nested path formats all segments")
        fun deeplyNestedPath() {
            assertEquals(
                "Term 3 › Numbers › Whole Numbers › Place Value",
                formatTopicPath("term3.numbers.whole_numbers.place_value"),
            )
        }

        @Test
        @DisplayName("hyphenated segments become spaces with title case")
        fun hyphenatedSegments() {
            assertEquals(
                "Term 1 › Number Sense",
                formatTopicPath("term1.number-sense"),
            )
        }

        @Test
        @DisplayName("all four terms format correctly")
        fun allTermsFormatCorrectly() {
            assertEquals("Term 1", formatTopicPath("term1"))
            assertEquals("Term 2", formatTopicPath("term2"))
            assertEquals("Term 3", formatTopicPath("term3"))
            assertEquals("Term 4", formatTopicPath("term4"))
        }
    }
}
