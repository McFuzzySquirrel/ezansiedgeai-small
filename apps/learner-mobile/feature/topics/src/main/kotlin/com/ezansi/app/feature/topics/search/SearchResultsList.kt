package com.ezansi.app.feature.topics.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezansi.app.core.ai.search.SearchResult
import com.ezansi.app.feature.topics.R

/**
 * Displays search results based on the current [SearchUiState].
 *
 * Handles five states:
 * 1. **Initial** (!hasSearched, no error) → shows nothing
 * 2. **Searching** (isSearching) → shows a loading spinner
 * 3. **Results** (hasSearched, results.isNotEmpty()) → LazyColumn of [SearchResultCard]s
 * 4. **No results** (hasSearched, results.isEmpty()) → "No matching topics found" (FT-FR-12)
 * 5. **Error** (error != null) → error message
 *
 * Uses [LazyColumn] for virtualised rendering to maintain 60 fps (NF-04).
 *
 * @param uiState Current search UI state from [SearchViewModel].
 * @param onAskAiClick Callback when learner taps "Ask AI" on a result.
 * @param modifier Modifier for the results container.
 */
@Composable
fun SearchResultsList(
    uiState: SearchUiState,
    onAskAiClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // Error state
        uiState.error != null -> {
            SearchErrorState(
                error = uiState.error,
                modifier = modifier,
            )
        }

        // Loading state
        uiState.isSearching -> {
            SearchLoadingState(modifier = modifier)
        }

        // Results found
        uiState.hasSearched && uiState.results.isNotEmpty() -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.results,
                    key = { result -> result.chunkId },
                ) { result ->
                    SearchResultCard(
                        result = result,
                        onAskAiClick = onAskAiClick,
                    )
                }
            }
        }

        // No results found
        uiState.hasSearched && uiState.results.isEmpty() -> {
            SearchEmptyState(modifier = modifier)
        }

        // Initial state — show nothing
        else -> {
            // No UI rendered before the first search
        }
    }
}

/**
 * Loading indicator shown while a search is in progress.
 */
@Composable
private fun SearchLoadingState(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.search_loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .semantics {
                contentDescription = loadingDescription
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Empty state shown when a search returns no results (FT-FR-12).
 * Suggests rephrasing or browsing topics.
 */
@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.search_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.search_no_results_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Error state shown when a search fails.
 */
@Composable
private fun SearchErrorState(
    error: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.search_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
