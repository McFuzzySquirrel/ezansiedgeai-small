package com.ezansi.app.feature.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.contentpack.TopicNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the topics browser screen.
 *
 * Immutable snapshot of everything the topics screen needs to render.
 * Updated atomically via [MutableStateFlow.update] to avoid partial-state bugs.
 */
data class TopicsUiState(
    /** Root-level topic tree (all packs merged). */
    val topicTree: List<TopicNode> = emptyList(),
    /** Navigation path as display names (e.g. ["Grade 6", "Term 1", "Fractions"]). */
    val breadcrumb: List<String> = emptyList(),
    /** Navigation path as TopicNode references for back-navigation. */
    val breadcrumbNodes: List<TopicNode?> = emptyList(),
    /** Children at the current navigation level. */
    val currentChildren: List<TopicNode> = emptyList(),
    /** Selected leaf topic (shows suggested questions). */
    val selectedTopic: TopicNode? = null,
    /** Contextual questions for the selected leaf topic. */
    val suggestedQuestions: List<String> = emptyList(),
    /** True while topic tree is loading from packs. */
    val isLoading: Boolean = false,
    /** True if at least one content pack is installed. */
    val hasContentPacks: Boolean = true,
    /** Error message to display, if any. */
    val error: String? = null,
)

/**
 * ViewModel for the topics browser — CAPS curriculum navigation.
 *
 * Loads the topic tree from all installed content packs and provides
 * hierarchical navigation (drill-down and breadcrumb back-navigation).
 * When a leaf topic is selected, generates contextual suggested
 * questions that the learner can tap to navigate to Chat.
 *
 * All state is exposed via [uiState] as an immutable [StateFlow] so the
 * Compose UI recomposes only when state actually changes.
 *
 * @param contentPackRepository Repository for accessing content pack data.
 */
class TopicsViewModel(
    private val contentPackRepository: ContentPackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicsUiState())
    val uiState: StateFlow<TopicsUiState> = _uiState.asStateFlow()

    init {
        loadTopics()
    }

    /**
     * Loads topic trees from all installed content packs.
     *
     * Iterates over installed packs, collects their topic trees, and
     * merges them into a single root-level list. If no packs are
     * installed, sets [TopicsUiState.hasContentPacks] to false.
     */
    fun loadTopics() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val packsResult = contentPackRepository.getInstalledPacks()) {
                is EzansiResult.Success -> {
                    val packs = packsResult.data
                    if (packs.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasContentPacks = false,
                                topicTree = emptyList(),
                                currentChildren = emptyList(),
                            )
                        }
                        return@launch
                    }

                    val allTopics = mutableListOf<TopicNode>()
                    for (pack in packs) {
                        when (val topicsResult = contentPackRepository.getTopicsForPack(pack.packId)) {
                            is EzansiResult.Success -> {
                                allTopics.addAll(topicsResult.data)
                            }
                            is EzansiResult.Error -> {
                                // Skip packs that fail to load topics — non-fatal
                            }
                            is EzansiResult.Loading -> { /* No-op */ }
                        }
                    }

                    val mergedTopics = mergeTopicTrees(allTopics)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasContentPacks = true,
                            topicTree = mergedTopics,
                            currentChildren = mergedTopics,
                            breadcrumb = emptyList(),
                            breadcrumbNodes = emptyList(),
                            selectedTopic = null,
                            suggestedQuestions = emptyList(),
                        )
                    }
                }

                is EzansiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasContentPacks = false,
                            error = packsResult.message,
                        )
                    }
                }

                is EzansiResult.Loading -> { /* No-op for suspending function */ }
            }
        }
    }

    /**
     * Navigates into a topic node, showing its children.
     *
     * For branch nodes (has children): pushes the topic onto the
     * breadcrumb and shows its children.
     *
     * For leaf nodes (no children): selects the topic and generates
     * suggested questions.
     *
     * @param topicNode The topic to navigate into or select.
     */
    fun navigateToTopic(topicNode: TopicNode) {
        if (topicNode.children.isNotEmpty()) {
            // Branch node — drill deeper
            _uiState.update { state ->
                state.copy(
                    currentChildren = topicNode.children,
                    breadcrumb = state.breadcrumb + formatDisplayName(topicNode.name),
                    breadcrumbNodes = state.breadcrumbNodes + topicNode,
                    selectedTopic = null,
                    suggestedQuestions = emptyList(),
                )
            }
        } else {
            // Leaf node — select and show suggested questions
            onTopicSelected(topicNode)
        }
    }

    /**
     * Navigates back one level in the topic hierarchy.
     *
     * If at the root level, this is a no-op (the system back button
     * should navigate away from the Topics screen entirely).
     *
     * @return `true` if navigation happened, `false` if already at root.
     */
    fun navigateBack(): Boolean {
        val state = _uiState.value

        // If a leaf topic is selected, deselect it first
        if (state.selectedTopic != null) {
            _uiState.update {
                it.copy(
                    selectedTopic = null,
                    suggestedQuestions = emptyList(),
                )
            }
            return true
        }

        // If at root, nothing to go back to
        if (state.breadcrumb.isEmpty()) {
            return false
        }

        // Pop one level from the breadcrumb
        val newBreadcrumb = state.breadcrumb.dropLast(1)
        val newBreadcrumbNodes = state.breadcrumbNodes.dropLast(1)

        val newChildren = if (newBreadcrumbNodes.isEmpty()) {
            state.topicTree
        } else {
            newBreadcrumbNodes.last()?.children ?: state.topicTree
        }

        _uiState.update {
            it.copy(
                currentChildren = newChildren,
                breadcrumb = newBreadcrumb,
                breadcrumbNodes = newBreadcrumbNodes,
                selectedTopic = null,
                suggestedQuestions = emptyList(),
            )
        }
        return true
    }

    /**
     * Navigates to a specific breadcrumb level.
     *
     * @param index The breadcrumb index to navigate to (0 = first level).
     *              Use -1 or omit to navigate to root.
     */
    fun navigateToBreadcrumb(index: Int) {
        val state = _uiState.value

        if (index < 0 || index >= state.breadcrumb.size) {
            // Navigate to root
            navigateToRoot()
            return
        }

        val newBreadcrumb = state.breadcrumb.take(index + 1)
        val newBreadcrumbNodes = state.breadcrumbNodes.take(index + 1)

        val newChildren = newBreadcrumbNodes.last()?.children ?: state.topicTree

        _uiState.update {
            it.copy(
                currentChildren = newChildren,
                breadcrumb = newBreadcrumb,
                breadcrumbNodes = newBreadcrumbNodes,
                selectedTopic = null,
                suggestedQuestions = emptyList(),
            )
        }
    }

    /**
     * Returns to the root of the topic tree.
     */
    fun navigateToRoot() {
        _uiState.update {
            it.copy(
                currentChildren = it.topicTree,
                breadcrumb = emptyList(),
                breadcrumbNodes = emptyList(),
                selectedTopic = null,
                suggestedQuestions = emptyList(),
            )
        }
    }

    /** Dismisses the current error message. */
    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Merges topic trees from multiple content packs.
     *
     * When two packs share a path (e.g. both have "term1"), their children
     * are merged recursively and chunk counts are summed. Without this,
     * LazyColumn crashes on duplicate keys.
     */
    private fun mergeTopicTrees(nodes: List<TopicNode>): List<TopicNode> {
        return nodes
            .groupBy { it.path }
            .map { (path, group) ->
                if (group.size == 1) {
                    group.first()
                } else {
                    TopicNode(
                        path = path,
                        name = group.first().name,
                        children = mergeTopicTrees(group.flatMap { it.children }),
                        chunkCount = group.sumOf { it.chunkCount },
                    )
                }
            }
    }

    /**
     * Selects a leaf topic and generates suggested questions.
     *
     * @param topicNode The leaf topic to select.
     */
    private fun onTopicSelected(topicNode: TopicNode) {
        val questions = SuggestedQuestions.generate(
            topicPath = topicNode.path,
            topicName = topicNode.name,
        )

        _uiState.update {
            it.copy(
                selectedTopic = topicNode,
                suggestedQuestions = questions,
            )
        }
    }

    /**
     * Converts a path segment into a readable display name.
     * "whole_numbers" → "Whole Numbers", "term1" → "Term 1"
     */
    private fun formatDisplayName(name: String): String {
        // Handle "termN" patterns → "Term N"
        val termMatch = Regex("^term(\\d+)$").find(name.lowercase())
        if (termMatch != null) {
            return "Term ${termMatch.groupValues[1]}"
        }

        return name
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}

/**
 * Factory for creating [TopicsViewModel] with manual dependency injection.
 *
 * Takes the [ContentPackRepository] interface rather than AppContainer
 * to avoid a circular module dependency (feature:topics cannot depend
 * on :app). The caller in the NavHost constructs this factory from
 * the AppContainer.
 *
 * Used with [ViewModelProvider] in the Composable via `viewModel(factory = ...)`.
 */
class TopicsViewModelFactory(
    private val contentPackRepository: ContentPackRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopicsViewModel::class.java)) {
            return TopicsViewModel(
                contentPackRepository = contentPackRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
