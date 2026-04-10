package com.ezansi.app.feature.topics.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ezansi.app.core.ai.search.ContentSearchEngine

/**
 * Factory for creating [SearchViewModel] with manual dependency injection.
 *
 * Takes the [ContentSearchEngine] interface rather than AppContainer
 * to avoid a circular module dependency (feature:topics cannot depend
 * on :app). The caller in the NavHost constructs this factory from
 * the AppContainer.
 *
 * Used with [ViewModelProvider] in the Composable via `viewModel(factory = ...)`.
 */
class SearchViewModelFactory(
    private val contentSearchEngine: ContentSearchEngine,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(
                contentSearchEngine = contentSearchEngine,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
