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
 * Unit tests for [SearchViewModel] — semantic search orchestrator.
 *
 * Tests state management, debounce behaviour, result mapping, error
 * handling, and clear/reset flow via a fake [ContentSearchEngine].
 *
 * Uses [Dispatchers.setMain] with [UnconfinedTestDispatcher] so coroutines
 * launched in viewModelScope execute synchronously — no flaky timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SearchViewModel")
class SearchViewModelTest {

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

        var lastQuery: String? = null
        var lastMaxResults: Int? = null

        override suspend fun search(query: String, maxResults: Int): EzansiResult<List<SearchResult>> {
            lastQuery = query
            lastMaxResults = maxResults
            return searchResults
        }

        override fun isReady(): Boolean = true
    }

    // ── Helper factories ────────────────────────────────────────────

    private fun createViewModel(
        engine: FakeContentSearchEngine = FakeContentSearchEngine(),
    ): Pair<SearchViewModel, FakeContentSearchEngine> {
        return SearchViewModel(contentSearchEngine = engine) to engine
    }

    private fun createSampleResults(count: Int = 3): List<SearchResult> {
        return (1..count).map { i ->
            SearchResult(
                chunkId = "chunk-$i",
                title = "Result $i",
                snippet = "This is the snippet for result $i about fractions.",
                topicPath = "term1.fractions.addition",
                score = 1f - (i * 0.1f),
                packId = "pack-1",
                chunk = ContentChunk(
                    chunkId = "chunk-$i",
                    packId = "pack-1",
                    title = "Result $i",
                    topicPath = "term1.fractions.addition",
                    content = "Full content for result $i.",
                ),
            )
        }
    }

    // ── Initial state ───────────────────────────────────────────────

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        @DisplayName("starts with empty query")
        fun emptyQuery() {
            val (vm, _) = createViewModel()
            assertEquals("", vm.uiState.value.query)
        }

        @Test
        @DisplayName("starts with no results")
        fun noResults() {
            val (vm, _) = createViewModel()
            assertTrue(vm.uiState.value.results.isEmpty())
        }

        @Test
        @DisplayName("starts not searching")
        fun notSearching() {
            val (vm, _) = createViewModel()
            assertFalse(vm.uiState.value.isSearching)
        }

        @Test
        @DisplayName("starts with no error")
        fun noError() {
            val (vm, _) = createViewModel()
            assertNull(vm.uiState.value.error)
        }

        @Test
        @DisplayName("starts with hasSearched false")
        fun hasNotSearched() {
            val (vm, _) = createViewModel()
            assertFalse(vm.uiState.value.hasSearched)
        }
    }

    // ── Query changes ───────────────────────────────────────────────

    @Nested
    @DisplayName("Query changes")
    inner class QueryChangeTests {

        @Test
        @DisplayName("onQueryChanged updates query text immediately")
        fun queryTextUpdated() = runTest {
            val (vm, _) = createViewModel()
            vm.onQueryChanged("fractions")
            assertEquals("fractions", vm.uiState.value.query)
        }

        @Test
        @DisplayName("blank query resets search state without triggering search")
        fun blankQueryResetsState() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(createSampleResults())
            }
            val (vm, _) = createViewModel(engine)

            // First do a search
            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            // Now clear
            vm.onQueryChanged("")

            assertFalse(vm.uiState.value.hasSearched)
            assertTrue(vm.uiState.value.results.isEmpty())
            assertFalse(vm.uiState.value.isSearching)
            assertNull(vm.uiState.value.error)
        }

        @Test
        @DisplayName("whitespace-only query resets search state")
        fun whitespaceOnlyQueryResetsState() = runTest {
            val (vm, _) = createViewModel()

            vm.onQueryChanged("   ")

            assertFalse(vm.uiState.value.hasSearched)
            assertTrue(vm.uiState.value.results.isEmpty())
        }
    }

    // ── Search with results ─────────────────────────────────────────

    @Nested
    @DisplayName("Search with results")
    inner class SearchWithResultsTests {

        @Test
        @DisplayName("search with results populates state")
        fun searchPopulatesResults() = runTest {
            val results = createSampleResults(3)
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(results)
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(3, state.results.size)
            assertTrue(state.hasSearched)
            assertFalse(state.isSearching)
            assertNull(state.error)
            assertEquals("chunk-1", state.results[0].chunkId)
        }

        @Test
        @DisplayName("search query is trimmed before sending to engine")
        fun queryIsTrimmed() = runTest {
            val engine = FakeContentSearchEngine()
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("  fractions  ")
            advanceUntilIdle()

            assertEquals("fractions", engine.lastQuery)
        }
    }

    // ── Search with no results ──────────────────────────────────────

    @Nested
    @DisplayName("Search with no results")
    inner class SearchNoResultsTests {

        @Test
        @DisplayName("empty results sets hasSearched true with empty list")
        fun emptyResultsSetsHasSearched() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(emptyList())
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("nonexistent topic xyz")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.hasSearched)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
            assertNull(state.error)
        }
    }

    // ── Search error ────────────────────────────────────────────────

    @Nested
    @DisplayName("Search error")
    inner class SearchErrorTests {

        @Test
        @DisplayName("engine error maps to error state")
        fun engineErrorMapsToErrorState() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Error("Embedding model not loaded")
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.hasSearched)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
            assertEquals("Embedding model not loaded", state.error)
        }

        @Test
        @DisplayName("blank error message falls back to default")
        fun blankErrorFallsBackToDefault() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Error("")
            }
            val (vm, _) = createViewModel(engine)

            vm.onQueryChanged("test")
            advanceUntilIdle()

            assertEquals("Search failed. Please try again.", vm.uiState.value.error)
        }
    }

    // ── Clear search ────────────────────────────────────────────────

    @Nested
    @DisplayName("Clear search")
    inner class ClearSearchTests {

        @Test
        @DisplayName("clearSearch resets all state to initial values")
        fun clearSearchResetsState() = runTest {
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(createSampleResults())
            }
            val (vm, _) = createViewModel(engine)

            // Do a search first
            vm.onQueryChanged("fractions")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.hasSearched)
            assertTrue(vm.uiState.value.results.isNotEmpty())

            // Now clear
            vm.clearSearch()

            val state = vm.uiState.value
            assertEquals("", state.query)
            assertTrue(state.results.isEmpty())
            assertFalse(state.isSearching)
            assertNull(state.error)
            assertFalse(state.hasSearched)
        }
    }

    // ── Explicit submit ─────────────────────────────────────────────

    @Nested
    @DisplayName("Explicit search submission")
    inner class SearchSubmitTests {

        @Test
        @DisplayName("onSearchSubmitted triggers search immediately")
        fun submitTriggersSearch() = runTest {
            val results = createSampleResults(2)
            val engine = FakeContentSearchEngine().apply {
                searchResults = EzansiResult.Success(results)
            }
            val (vm, _) = createViewModel(engine)

            // Set query without triggering debounce
            vm.onQueryChanged("fractions")
            // Submit immediately
            vm.onSearchSubmitted()
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.results.size)
            assertTrue(vm.uiState.value.hasSearched)
        }

        @Test
        @DisplayName("blank query on submit does nothing")
        fun blankQueryOnSubmitIgnored() = runTest {
            val engine = FakeContentSearchEngine()
            val (vm, _) = createViewModel(engine)

            vm.onSearchSubmitted()
            advanceUntilIdle()

            assertNull(engine.lastQuery)
            assertFalse(vm.uiState.value.hasSearched)
        }
    }

    // ── FormatTopicPath ─────────────────────────────────────────────

    @Nested
    @DisplayName("formatTopicPath")
    inner class FormatTopicPathTests {

        @Test
        @DisplayName("formats term and topic segments")
        fun formatsTermAndTopic() {
            assertEquals("Term 1 › Fractions", formatTopicPath("term1.fractions"))
        }

        @Test
        @DisplayName("formats underscored segments")
        fun formatsUnderscoredSegments() {
            assertEquals(
                "Term 2 › Whole Numbers › Addition",
                formatTopicPath("term2.whole_numbers.addition"),
            )
        }

        @Test
        @DisplayName("handles single segment")
        fun handlesSingleSegment() {
            assertEquals("Geometry", formatTopicPath("geometry"))
        }
    }
}
