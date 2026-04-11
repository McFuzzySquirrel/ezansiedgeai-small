package com.ezansi.app.feature.topics.search

import com.ezansi.app.core.ai.search.SearchResult

/**
 * UI state for the semantic search feature.
 *
 * Immutable snapshot of everything the search results area needs to render.
 * Updated atomically via [kotlinx.coroutines.flow.MutableStateFlow.update]
 * to avoid partial-state bugs.
 *
 * @param query Current text in the search input field.
 * @param results Ranked search results from [ContentSearchEngine].
 * @param isSearching True while a search query is in flight.
 * @param error Human-readable error message, if the last search failed.
 * @param hasSearched Distinguishes "no results found" from "haven't searched yet".
 */
data class SearchUiState(
    /** Current text in the search input field. */
    val query: String = "",
    /** Ranked search results from ContentSearchEngine. */
    val results: List<SearchResult> = emptyList(),
    /** True while a search query is in flight. */
    val isSearching: Boolean = false,
    /** Human-readable error message, if the last search failed. */
    val error: String? = null,
    /** Distinguishes "no results found" from "haven't searched yet". */
    val hasSearched: Boolean = false,
)
