package com.ezansi.app.feature.topics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezansi.app.core.data.contentpack.TopicNode
import com.ezansi.app.feature.topics.search.SearchResultsList
import com.ezansi.app.feature.topics.search.SearchViewModel

/**
 * Topics browser screen — browse CAPS-aligned curriculum topics.
 *
 * Provides hierarchical navigation through the curriculum:
 * Term → Strand → Topic → Suggested Questions → Chat.
 *
 * ## Layout
 *
 * - **Top bar**: Title with back button when navigating deeper
 * - **Breadcrumb trail**: Tappable segments showing current path
 * - **Topic cards**: LazyColumn of Material3 cards, each showing
 *   topic name, emoji icon, chunk count, and navigation arrow
 * - **Suggested questions**: When a leaf topic is tapped, shows
 *   3–5 contextual questions the learner can ask
 * - **Zero-pack state**: Friendly empty state with Library link
 *
 * ## Accessibility
 *
 * - All touch targets ≥ 48×48 dp (ACC-02)
 * - WCAG 2.1 AA contrast via theme colours (ACC-01)
 * - Content descriptions on all interactive elements (ACC-04)
 * - Respects system font-size scaling (ACC-03)
 * - TalkBack logical navigation order (ACC-04)
 *
 * @param viewModel The [TopicsViewModel] providing topic tree state.
 * @param searchViewModel The [SearchViewModel] providing semantic search state (FT-FR-06).
 * @param onNavigateToChat Callback to navigate to chat with a pre-filled question.
 * @param onNavigateToLibrary Callback to navigate to the content library.
 * @param modifier Modifier applied to the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    viewModel: TopicsViewModel,
    searchViewModel: SearchViewModel,
    onNavigateToChat: (question: String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()

    // Handle system back button — go up one topic level before exiting
    BackHandler(enabled = state.breadcrumb.isNotEmpty() || state.selectedTopic != null) {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.topics_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (state.breadcrumb.isNotEmpty() || state.selectedTopic != null) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.topics_navigate_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // ── Search bar (FT-FR-06) ───────────────────────────────
            // Shown when content packs are loaded (no use searching empty state).
            // Search bar sits between TopAppBar and the content area.
            if (!state.isLoading && state.error == null && state.hasContentPacks) {
                TopicSearchBar(
                    query = searchState.query,
                    onQueryChanged = searchViewModel::onQueryChanged,
                    onSearchSubmitted = searchViewModel::onSearchSubmitted,
                    onClearSearch = searchViewModel::clearSearch,
                )
            }

            // ── Content area ────────────────────────────────────────
            // When search query is active, show search results instead
            // of the normal topic browser. Otherwise show normal content.
            val isSearchActive = searchState.query.isNotBlank()

            when {
                // Search is active — show search results (FT-FR-06)
                isSearchActive -> {
                    SearchResultsList(
                        uiState = searchState,
                        onAskAiClick = { result ->
                            // F4.5: "Ask AI" navigates to ChatWithQuestion (FT-FR-07)
                            val question = "Explain ${result.title}"
                            onNavigateToChat(question)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.isLoading -> {
                    LoadingState()
                }

                state.error != null -> {
                    ErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.loadTopics() },
                    )
                }

                !state.hasContentPacks -> {
                    ZeroPackState(
                        onNavigateToLibrary = onNavigateToLibrary,
                    )
                }

                state.selectedTopic != null -> {
                    SelectedTopicContent(
                        topic = state.selectedTopic!!,
                        suggestedQuestions = state.suggestedQuestions,
                        breadcrumb = state.breadcrumb,
                        onQuestionTapped = onNavigateToChat,
                        onBreadcrumbTapped = { index -> viewModel.navigateToBreadcrumb(index) },
                        onHomeTapped = { viewModel.navigateToRoot() },
                    )
                }

                else -> {
                    TopicBrowserContent(
                        children = state.currentChildren,
                        breadcrumb = state.breadcrumb,
                        onTopicTapped = { topic -> viewModel.navigateToTopic(topic) },
                        onBreadcrumbTapped = { index -> viewModel.navigateToBreadcrumb(index) },
                        onHomeTapped = { viewModel.navigateToRoot() },
                    )
                }
            }
        }
    }
}

// ── Search Bar ───────────────────────────────────────────────────────

/**
 * Search bar for semantic search within topics (FT-FR-06).
 *
 * Material 3 OutlinedTextField with search icon (leading) and clear
 * button (trailing, shown when query is non-empty). All interactive
 * elements are ≥ 48 dp (ACC-02) with content descriptions for
 * TalkBack (ACC-04).
 *
 * @param query Current search query text.
 * @param onQueryChanged Called on every keystroke for debounced search.
 * @param onSearchSubmitted Called when the keyboard "done" action fires.
 * @param onClearSearch Called when the clear button is tapped.
 * @param modifier Modifier for the search bar container.
 */
@Composable
private fun TopicSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.search_bar_hint)
    val clearDescription = stringResource(R.string.search_bar_clear)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = searchHint,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null, // Decorative — field label provides context
                modifier = Modifier.size(24.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier.size(48.dp), // ACC-02: 48dp touch target
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = clearDescription,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearchSubmitted() },
        ),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

// ── Topic Browser Content ───────────────────────────────────────────

/**
 * Main content: breadcrumb trail + topic card list.
 */
@Composable
private fun TopicBrowserContent(
    children: List<TopicNode>,
    breadcrumb: List<String>,
    onTopicTapped: (TopicNode) -> Unit,
    onBreadcrumbTapped: (Int) -> Unit,
    onHomeTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (breadcrumb.isNotEmpty()) {
            BreadcrumbTrail(
                breadcrumb = breadcrumb,
                onSegmentTapped = onBreadcrumbTapped,
                onHomeTapped = onHomeTapped,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = children,
                key = { it.path },
            ) { topic ->
                TopicCard(
                    topic = topic,
                    onTapped = { onTopicTapped(topic) },
                )
            }
        }
    }
}

// ── Breadcrumb Trail ────────────────────────────────────────────────

/**
 * Horizontal breadcrumb showing the current navigation path.
 *
 * Each segment is tappable to navigate back to that level.
 * "Home" is always the first segment.
 */
@Composable
private fun BreadcrumbTrail(
    breadcrumb: List<String>,
    onSegmentTapped: (Int) -> Unit,
    onHomeTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val breadcrumbText = buildString {
        append(stringResource(R.string.topics_breadcrumb_home))
        breadcrumb.forEach { segment ->
            append(" / ")
            append(segment)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = breadcrumbText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Home segment
        Text(
            text = stringResource(R.string.topics_breadcrumb_home),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onHomeTapped)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )

        // Path segments
        breadcrumb.forEachIndexed { index, segment ->
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val isLast = index == breadcrumb.lastIndex
            Text(
                text = segment,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLast) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                modifier = if (!isLast) {
                    Modifier
                        .clickable { onSegmentTapped(index) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                } else {
                    Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                },
            )
        }
    }
}

// ── Topic Card ──────────────────────────────────────────────────────

/**
 * A single topic card in the list.
 *
 * Branch topics (has children) show a forward arrow.
 * Leaf topics (no children) show the chunk count.
 */
@Composable
private fun TopicCard(
    topic: TopicNode,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBranch = topic.children.isNotEmpty()
    val emoji = getTopicEmoji(topic.name, topic.path)
    val chunkLabel = if (topic.chunkCount == 1) {
        stringResource(R.string.topics_chunk_count_one)
    } else {
        stringResource(R.string.topics_chunk_count, topic.chunkCount)
    }

    val cardDescription = if (isBranch) {
        stringResource(R.string.topics_card_branch_label, formatDisplayName(topic.name), topic.chunkCount)
    } else {
        stringResource(R.string.topics_card_label, formatDisplayName(topic.name), topic.chunkCount)
    }

    Card(
        onClick = onTapped,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Topic emoji icon
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Topic name and chunk count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDisplayName(topic.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = chunkLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Navigation indicator
            if (isBranch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.topics_has_children),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ── Selected Topic (Suggested Questions) ────────────────────────────

/**
 * Shows a selected leaf topic with suggested questions.
 *
 * The learner tapped a leaf topic and now sees contextual questions
 * they can ask. Tapping a question navigates to Chat.
 */
@Composable
private fun SelectedTopicContent(
    topic: TopicNode,
    suggestedQuestions: List<String>,
    breadcrumb: List<String>,
    onQuestionTapped: (String) -> Unit,
    onBreadcrumbTapped: (Int) -> Unit,
    onHomeTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (breadcrumb.isNotEmpty()) {
            BreadcrumbTrail(
                breadcrumb = breadcrumb,
                onSegmentTapped = onBreadcrumbTapped,
                onHomeTapped = onHomeTapped,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Selected topic header
            item(key = "header") {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = getTopicEmoji(topic.name, topic.path),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = formatDisplayName(topic.name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics {
                                    contentDescription = "Selected topic: ${formatDisplayName(topic.name)}"
                                },
                            )
                            val chunkLabel = if (topic.chunkCount == 1) {
                                stringResource(R.string.topics_chunk_count_one)
                            } else {
                                stringResource(R.string.topics_chunk_count, topic.chunkCount)
                            }
                            Text(
                                text = chunkLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.topics_questions_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }

            // Suggested question cards
            items(
                items = suggestedQuestions,
                key = { it },
            ) { question ->
                SuggestedQuestionCard(
                    question = question,
                    onTapped = { onQuestionTapped(question) },
                )
            }
        }
    }
}

/**
 * A tappable card showing a suggested question.
 *
 * Tapping navigates to Chat with this question pre-filled and sent.
 */
@Composable
private fun SuggestedQuestionCard(
    question: String,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val questionDescription = stringResource(R.string.topics_question_label, question)

    OutlinedCard(
        onClick = onTapped,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = questionDescription },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.topics_ask_question),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── Empty / Error / Loading States ──────────────────────────────────

/**
 * Zero-pack state — no content packs installed (CP-09).
 *
 * Shows a friendly message and a button to navigate to the Library.
 */
@Composable
private fun ZeroPackState(
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "📚",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.topics_no_packs_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.topics_no_packs_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToLibrary,
            modifier = Modifier.height(48.dp),
        ) {
            Text(text = stringResource(R.string.topics_go_to_library))
        }
    }
}

/**
 * Loading state — shown while topic tree is being built.
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.topics_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Error state — shown when topic loading fails.
 */
@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.topics_error_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp),
        ) {
            Text(text = stringResource(R.string.topics_retry))
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

/**
 * Returns an emoji icon for a topic based on its name or path.
 *
 * Matches common CAPS maths topic names to relevant emojis for
 * visual interest and quick recognition on small screens.
 */
private fun getTopicEmoji(name: String, path: String): String {
    val lower = name.lowercase()
    val pathLower = path.lowercase()

    return when {
        // Terms
        lower.startsWith("term") -> "📅"

        // Numbers & Operations
        lower.contains("number") || lower.contains("whole") -> "🔢"
        lower.contains("fraction") -> "🍕"
        lower.contains("decimal") -> "🔣"
        lower.contains("percent") -> "💯"
        lower.contains("operation") -> "➕"
        lower.contains("addition") || lower.contains("add") -> "➕"
        lower.contains("subtraction") || lower.contains("subtract") -> "➖"
        lower.contains("multiplication") || lower.contains("multiply") -> "✖️"
        lower.contains("division") || lower.contains("divide") -> "➗"

        // Patterns & Algebra
        lower.contains("pattern") -> "🔄"
        lower.contains("algebra") -> "🧮"

        // Geometry
        lower.contains("geometry") || lower.contains("shape") -> "📐"
        lower.contains("angle") -> "📏"
        lower.contains("symmetry") -> "🦋"
        lower.contains("transform") -> "🔀"
        lower.contains("3d") || lower.contains("solid") -> "🧊"

        // Measurement
        lower.contains("measure") -> "📏"
        lower.contains("length") -> "📏"
        lower.contains("mass") || lower.contains("weight") -> "⚖️"
        lower.contains("volume") || lower.contains("capacity") -> "🫗"
        lower.contains("area") -> "⬜"
        lower.contains("perimeter") -> "🔲"
        lower.contains("time") -> "⏰"

        // Data Handling
        lower.contains("data") -> "📊"
        lower.contains("graph") -> "📈"
        lower.contains("probability") -> "🎲"

        // Structural / generic
        lower.contains("basic") -> "📖"
        lower.contains("comparing") || lower.contains("compare") -> "⚖️"
        lower.contains("equivalent") -> "🟰"
        lower.contains("simplif") -> "✂️"

        // Path-based fallbacks
        pathLower.contains("geometry") -> "📐"
        pathLower.contains("data") -> "📊"
        pathLower.contains("measure") -> "📏"
        pathLower.contains("pattern") -> "🔄"
        pathLower.contains("number") -> "🔢"

        // Default
        else -> "📚"
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

