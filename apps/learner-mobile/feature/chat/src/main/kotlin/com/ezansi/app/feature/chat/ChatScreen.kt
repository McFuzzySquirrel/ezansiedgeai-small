package com.ezansi.app.feature.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezansi.app.feature.chat.ui.MarkdownText
import com.ezansi.app.feature.chat.onboarding.OnboardingManager
import com.ezansi.app.feature.chat.onboarding.OnboardingTooltip
import com.ezansi.app.feature.chat.onboarding.WelcomeBanner
import kotlinx.coroutines.delay

/**
 * Chat screen — the primary interaction surface for learners.
 *
 * Displays a scrollable message history with question/answer bubbles,
 * a text input field, and real-time pipeline status during generation.
 *
 * Design constraints (PRD §8.2, §11, §12):
 * - 48×48 dp minimum touch targets for all interactive elements
 * - WCAG 2.1 AA colour contrast via theme colours
 * - System font-size scaling respected (no hardcoded sp)
 * - Content descriptions on all interactive elements for TalkBack
 * - LazyColumn for virtualised message rendering (60 fps)
 * - No WebView — all content rendered natively
 *
 * @param viewModel The [ChatViewModel] providing state and actions.
 * @param modifier Layout modifier applied to the root container.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // ── Onboarding state (P2-108) ───────────────────────────────
    val onboardingManager = remember { OnboardingManager(context) }
    var showWelcomeBanner by remember {
        mutableStateOf(
            onboardingManager.shouldShowTip(OnboardingManager.TIP_WELCOME),
        )
    }
    var showTopicsHint by remember {
        mutableStateOf(
            onboardingManager.shouldShowTip(OnboardingManager.TIP_FIRST_QUESTION),
        )
    }
    var showProfileHint by remember {
        mutableStateOf(
            onboardingManager.shouldShowTip(OnboardingManager.TIP_PROFILE_HINT),
        )
    }

    // Show topics hint after the first completed answer
    val hasCompletedAnswer = state.messages.any { it.answer != null && !it.isLoading }

    // Auto-scroll to bottom when a new message is added
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // ── Top bar ─────────────────────────────────────────────
        ChatTopBar(
            profileName = state.activeProfileName,
            onClearHistory = viewModel::onClearHistory,
        )

        HorizontalDivider()

        // ── Welcome banner (P2-108) ─────────────────────────────
        // Shows only on first launch when no profile exists and tip not dismissed.
        // Zero-step onboarding — purely informational, app is fully usable without it.
        if (showWelcomeBanner && state.activeProfileId == null) {
            WelcomeBanner(
                onDismiss = {
                    onboardingManager.dismissTip(OnboardingManager.TIP_WELCOME)
                    showWelcomeBanner = false
                },
            )
        }

        // ── Profile creation hint ───────────────────────────────
        // Shows if no profile exists and the welcome banner was already dismissed.
        if (!showWelcomeBanner && showProfileHint && state.activeProfileId == null) {
            OnboardingTooltip(
                text = stringResource(R.string.onboarding_profile_hint),
                onDismiss = {
                    onboardingManager.dismissTip(OnboardingManager.TIP_PROFILE_HINT)
                    showProfileHint = false
                },
            )
        }

        // ── No content packs banner ─────────────────────────────
        if (!state.hasContentPacks) {
            NoPacksBanner()
        }

        // ── Message list or empty state ─────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.messages.isEmpty() && !state.isGenerating) {
                EmptyStateContent(
                    onExampleClick = { question ->
                        viewModel.onInputChanged(question)
                        viewModel.onSendMessage()
                    },
                )
            } else {
                ChatMessageList(
                    messages = state.messages,
                    listState = listState,
                    onCopyAnswer = { answerText ->
                        clipboardManager.setText(AnnotatedString(answerText))
                        Toast.makeText(
                            context,
                            context.getString(R.string.chat_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }

        // ── Topics hint after first answer (P2-108) ─────────────
        // Shows after the learner's first completed Q&A exchange.
        if (hasCompletedAnswer && showTopicsHint) {
            OnboardingTooltip(
                text = stringResource(R.string.onboarding_topics_hint),
                onDismiss = {
                    onboardingManager.dismissTip(OnboardingManager.TIP_FIRST_QUESTION)
                    showTopicsHint = false
                },
            )
        }

        // ── Error banner ────────────────────────────────────────
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            state.errorMessage?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = viewModel::onDismissError,
                )
            }
        }

        HorizontalDivider()

        // ── Input bar ───────────────────────────────────────────
        ChatInputBar(
            inputText = state.inputText,
            isGenerating = state.isGenerating,
            onInputChanged = viewModel::onInputChanged,
            onSendMessage = viewModel::onSendMessage,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════
// Internal composables — each handles one visual section of the screen
// ═════════════════════════════════════════════════════════════════════

/**
 * Top bar showing the app title and active profile name.
 * Includes a clear-history action button.
 */
@Composable
private fun ChatTopBar(
    profileName: String?,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clearDescription = stringResource(R.string.chat_clear_description)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (profileName != null) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = clearDescription
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null, // Set on parent
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Scrollable message list using [LazyColumn] for virtualised rendering.
 * Each item renders a question bubble, and either an answer bubble
 * or a loading indicator depending on the message state.
 */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessageUi>,
    listState: LazyListState,
    onCopyAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Padding at top and bottom of the list for breathing room
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = 8.dp,
        ),
    ) {
        items(
            items = messages,
            key = { it.id },
            contentType = { "message" },
        ) { message ->
            MessageItem(
                message = message,
                onCopyAnswer = onCopyAnswer,
            )
        }
    }
}

/**
 * A single message item: the learner's question bubble followed by
 * the AI's answer bubble (or loading indicator / error state).
 */
@Composable
private fun MessageItem(
    message: ChatMessageUi,
    onCopyAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val questionDescription = stringResource(R.string.chat_question_label, message.question)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // ── Question bubble (right-aligned, primary colour) ─────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .semantics {
                        contentDescription = questionDescription
                    },
            ) {
                Text(
                    text = message.question,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Answer section (left-aligned) ───────────────────────
        when {
            message.isLoading -> {
                LoadingBubble(pipelineState = message.pipelineState)
            }
            message.pipelineState == PipelineState.ERROR -> {
                ErrorBubble()
            }
            message.answer != null -> {
                AnswerBubble(
                    answer = message.answer,
                    sources = message.sources,
                    onCopy = { onCopyAnswer(message.answer) },
                )
            }
        }
    }
}

/**
 * Answer bubble with Markdown-rendered text, source attribution chips,
 * and a copy button.
 */
@Composable
private fun AnswerBubble(
    answer: String,
    sources: List<SourceUi>,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val answerDescription = stringResource(R.string.chat_answer_label, answer)
    val sourcesDescription = stringResource(R.string.chat_sources_label)
    val copyDescription = stringResource(R.string.chat_copy_description)
    val copyLabel = stringResource(R.string.chat_copy_button)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .semantics {
                    contentDescription = answerDescription
                },
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Markdown-rendered answer text
                MarkdownText(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Source attribution chips
                if (sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.semantics {
                            contentDescription = sourcesDescription
                        },
                    ) {
                        sources.take(3).forEach { source ->
                            SourceChip(source = source)
                        }
                    }
                }

                // Copy button (CH-08)
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = copyDescription
                        },
                ) {
                    Text(
                        text = copyLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * Small chip showing a content source title and relevance.
 */
@Composable
private fun SourceChip(
    source: SourceUi,
    modifier: Modifier = Modifier,
) {
    val sourceDescription = stringResource(R.string.chat_source_label, source.title)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.semantics {
            contentDescription = sourceDescription
        },
    ) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Loading indicator shown while the pipeline is processing.
 * Displays the current pipeline state and elapsed time (CH-07).
 */
@Composable
private fun LoadingBubble(
    pipelineState: PipelineState?,
    modifier: Modifier = Modifier,
) {
    // Elapsed time counter — increments every second while visible
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            elapsedSeconds++
        }
    }

    val stateText = when (pipelineState) {
        PipelineState.THINKING -> stringResource(R.string.chat_thinking)
        PipelineState.RETRIEVING -> stringResource(R.string.chat_retrieving)
        PipelineState.GENERATING -> stringResource(R.string.chat_generating)
        PipelineState.ERROR -> stringResource(R.string.chat_error_generic)
        null -> ""
    }

    val loadingDescription = stringResource(R.string.chat_loading_label, stateText)
    val elapsedText = stringResource(R.string.chat_elapsed, elapsedSeconds)

    Row(
        modifier = modifier
            .padding(start = 8.dp)
            .semantics { contentDescription = loadingDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$stateText $elapsedText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Error state bubble shown when the pipeline fails for a specific message.
 */
@Composable
private fun ErrorBubble(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.chat_error_generic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Empty state shown when no messages exist.
 * Displays a welcome message and tappable example questions.
 */
@Composable
private fun EmptyStateContent(
    onExampleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.chat_empty_try),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // Example question cards — each is a tappable card
        val examples = listOf(
            stringResource(R.string.chat_example_1),
            stringResource(R.string.chat_example_2),
            stringResource(R.string.chat_example_3),
        )
        examples.forEach { question ->
            ExampleQuestionCard(
                question = question,
                onClick = { onExampleClick(question) },
            )
        }
    }
}

/**
 * Tappable card showing an example question.
 * Minimum 48 dp height for accessibility (ACC-02).
 */
@Composable
private fun ExampleQuestionCard(
    question: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = question },
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(16.dp)
                .heightIn(min = 24.dp), // 24 dp text + 32 dp padding = 56 dp total > 48 dp
        )
    }
}

/**
 * Text input field with send button at the bottom of the chat screen.
 * Send button is disabled during generation or when input is empty.
 * All touch targets are ≥48×48 dp (ACC-02, CH-06).
 */
@Composable
private fun ChatInputBar(
    inputText: String,
    isGenerating: Boolean,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sendEnabled = !isGenerating && inputText.isNotBlank()
    val sendDescription = stringResource(R.string.chat_send_description)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (sendEnabled) onSendMessage() },
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSendMessage,
                enabled = sendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = sendDescription },
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null, // Set on parent
                    tint = if (sendEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

/**
 * Banner shown when no content packs are installed.
 * Guides the learner to install a pack (CP-09).
 */
@Composable
private fun NoPacksBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.chat_no_packs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Error banner at the bottom of the message area.
 * Shows a dismissible error message with a close button.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissDescription = stringResource(R.string.chat_dismiss_error)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = dismissDescription },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null, // Set on parent
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
