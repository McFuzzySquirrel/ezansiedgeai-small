package com.ezansi.app.feature.topics.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.ai.search.ContentSearchEngine
import com.ezansi.app.core.common.EzansiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Debounce delay for search-as-you-type (FT-FR-06). */
private const val SEARCH_DEBOUNCE_MS = 300L

/** Maximum results to request from ContentSearchEngine (FT-FR-08). */
private const val MAX_SEARCH_RESULTS = 10

/**
 * Error message constants — match values in strings.xml for consistency.
 * When localisation is added, refactor to AndroidViewModel with Application
 * context for resource access.
 */
private const val ERROR_SEARCH_FAILED = "Search failed. Please try again."

/**
 * ViewModel for semantic search within the topics browser.
 *
 * Orchestrates the search flow:
 * 1. Accepts query text from the search bar (debounced 300 ms)
 * 2. Calls [ContentSearchEngine.search] to embed + retrieve + rank
 * 3. Maps [EzansiResult] to [SearchUiState] for the Compose UI
 *
 * All state is exposed via [uiState] as an immutable [StateFlow] so the
 * Compose UI recomposes only when state actually changes.
 *
 * @param contentSearchEngine The search-without-generation engine (FT-FR-07).
 */
class SearchViewModel(
    private val contentSearchEngine: ContentSearchEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Tracks the debounced search job so we can cancel it on new input. */
    private var searchJob: Job? = null

    /**
     * Called on every keystroke in the search bar.
     *
     * Updates the query text immediately (for responsive UI), then
     * debounces 300 ms before triggering the search. A new keystroke
     * cancels the previous debounce timer.
     *
     * @param query The current search bar text.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }

        // Cancel any in-flight debounce
        searchJob?.cancel()

        // Don't search for blank queries
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    error = null,
                    hasSearched = false,
                )
            }
            return
        }

        // Debounce: wait 300 ms, then fire the search
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            executeSearch(query.trim())
        }
    }

    /**
     * Called when the user explicitly submits the search (e.g. keyboard "done").
     *
     * Triggers the search immediately without debounce.
     */
    fun onSearchSubmitted() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        // Cancel any pending debounce
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            executeSearch(query)
        }
    }

    /**
     * Resets all search state to initial values.
     *
     * Called when the user clears the search bar or navigates away.
     */
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { SearchUiState() }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Executes a search query against the [ContentSearchEngine].
     *
     * Sets [SearchUiState.isSearching] before the call and maps the
     * [EzansiResult] to the appropriate UI state on completion.
     */
    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, error = null) }

        when (val result = contentSearchEngine.search(query, MAX_SEARCH_RESULTS)) {
            is EzansiResult.Success -> {
                _uiState.update {
                    it.copy(
                        results = result.data,
                        isSearching = false,
                        hasSearched = true,
                        error = null,
                    )
                }
            }
            is EzansiResult.Error -> {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        isSearching = false,
                        hasSearched = true,
                        error = result.message.ifBlank { ERROR_SEARCH_FAILED },
                    )
                }
            }
            is EzansiResult.Loading -> {
                // No-op — Loading is a UI-only state, not returned by suspend fns
            }
        }
    }
}
