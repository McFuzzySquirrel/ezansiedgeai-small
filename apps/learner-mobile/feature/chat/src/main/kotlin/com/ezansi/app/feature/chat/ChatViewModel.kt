package com.ezansi.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ezansi.app.core.ai.ContentSource
import com.ezansi.app.core.ai.ExplanationEngine
import com.ezansi.app.core.ai.ExplanationResult
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.ProfileRepository
import com.ezansi.app.core.data.chat.ChatHistoryRepository
import com.ezansi.app.core.data.chat.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * UI state for the chat screen.
 *
 * Immutable snapshot of everything the chat screen needs to render.
 * Updated atomically via [MutableStateFlow.update] to avoid partial-state bugs.
 */
data class ChatUiState(
    /** All messages in the current conversation, newest last. */
    val messages: List<ChatMessageUi> = emptyList(),
    /** Current text in the input field. */
    val inputText: String = "",
    /** True while the pipeline is actively generating an answer. */
    val isGenerating: Boolean = false,
    /** Current pipeline stage for the loading indicator. */
    val pipelineState: PipelineState? = null,
    /** Active learner's display name, shown in the app bar. */
    val activeProfileName: String? = null,
    /** Active learner's profile ID, used for API calls. */
    val activeProfileId: String? = null,
    /** True if at least one content pack is installed. */
    val hasContentPacks: Boolean = true,
    /** Error message to display, if any. */
    val errorMessage: String? = null,
)

/** Timeout threshold in millis — show a patience message after this (AI-09). */
private const val INFERENCE_TIMEOUT_MS = 30_000L

/**
 * Error message constants — match values in strings.xml for consistency.
 * When localisation is added, refactor ViewModel to use AndroidViewModel
 * with Application context for resource access.
 */
private const val ERROR_TIMEOUT = "This is taking longer than expected. Please wait\u2026"
private const val ERROR_GENERIC = "Something went wrong. Please try again."
private const val ERROR_NO_ACTIVE_PROFILE = "Please select or create a learner profile first."
private const val RETRIEVAL_MISS_PREFIX = "I couldn't find relevant content"

/**
 * ViewModel for the chat screen — the primary interaction surface.
 *
 * Orchestrates the conversation flow:
 * 1. Loads chat history from the local store on screen open
 * 2. Sends questions to [ExplanationEngine] and collects streaming results
 * 3. Persists completed messages via [ChatHistoryRepository]
 * 4. Tracks active profile and content pack availability
 *
 * All state is exposed via [uiState] as an immutable [StateFlow] so the
 * Compose UI recomposes only when state actually changes.
 */
class ChatViewModel(
    private val explanationEngine: ExplanationEngine,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val profileRepository: ProfileRepository,
    private val contentPackRepository: ContentPackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Tracks the timeout watcher so we can cancel it when generation completes. */
    private var timeoutJob: Job? = null

    init {
        loadActiveProfile()
        checkContentPacks()
    }

    /** Updates the input text field contents. */
    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Sends the current input text as a question to the explanation engine.
     *
     * Creates a placeholder message immediately (for instant UI feedback),
     * then collects the streaming [ExplanationResult] flow to update the
     * answer progressively. On completion, persists the message to history.
     */
    fun onSendMessage() {
        val question = _uiState.value.inputText.trim()
        if (question.isBlank()) return

        val profileId = _uiState.value.activeProfileId
        if (profileId == null) {
            // Recover from stale in-memory state: profile may have been created or
            // switched on another screen while this ViewModel stayed alive.
            viewModelScope.launch {
                when (val result = profileRepository.getActiveProfile()) {
                    is EzansiResult.Success -> {
                        val refreshedProfile = result.data
                        if (refreshedProfile != null) {
                            _uiState.update {
                                it.copy(
                                    activeProfileId = refreshedProfile.id,
                                    activeProfileName = refreshedProfile.name,
                                    errorMessage = null,
                                )
                            }
                            onSendMessage()
                        } else {
                            _uiState.update { it.copy(errorMessage = ERROR_NO_ACTIVE_PROFILE) }
                        }
                    }
                    is EzansiResult.Error -> {
                        _uiState.update {
                            it.copy(errorMessage = result.message.ifBlank { ERROR_NO_ACTIVE_PROFILE })
                        }
                    }
                    is EzansiResult.Loading -> {
                        _uiState.update { it.copy(errorMessage = ERROR_NO_ACTIVE_PROFILE) }
                    }
                }
            }
            return
        }

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Add a placeholder message with loading state immediately
        val placeholderMessage = ChatMessageUi(
            id = messageId,
            question = question,
            answer = null,
            sources = emptyList(),
            isLoading = true,
            pipelineState = PipelineState.THINKING,
            timestamp = timestamp,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + placeholderMessage,
                inputText = "",
                isGenerating = true,
                pipelineState = PipelineState.THINKING,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            // Start a 30-second timeout watcher (AI-09)
            timeoutJob?.cancel()
            timeoutJob = launch {
                delay(INFERENCE_TIMEOUT_MS)
                if (_uiState.value.isGenerating) {
                    _uiState.update {
                        it.copy(
                            errorMessage = ERROR_TIMEOUT,
                        )
                    }
                }
            }

            explanationEngine.explain(question, profileId)
                .catch { throwable ->
                    timeoutJob?.cancel()
                    updateLastMessage(messageId) { msg ->
                        msg.copy(
                            answer = null,
                            isLoading = false,
                            pipelineState = PipelineState.ERROR,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            pipelineState = PipelineState.ERROR,
                            errorMessage = ERROR_GENERIC,
                        )
                    }
                }
                .collect { result ->
                    when (result) {
                        is ExplanationResult.Thinking -> {
                            updatePipelineState(messageId, PipelineState.THINKING)
                        }

                        is ExplanationResult.Retrieving -> {
                            updatePipelineState(messageId, PipelineState.RETRIEVING)
                        }

                        is ExplanationResult.RuntimeStatus -> {
                            // Runtime status is for observability during validation runs.
                            // No chat UI state change is required.
                        }

                        is ExplanationResult.Generating -> {
                            updateLastMessage(messageId) { msg ->
                                msg.copy(
                                    answer = result.partialText,
                                    pipelineState = PipelineState.GENERATING,
                                )
                            }
                            _uiState.update {
                                it.copy(pipelineState = PipelineState.GENERATING)
                            }
                        }

                        is ExplanationResult.Complete -> {
                            timeoutJob?.cancel()
                            val sourceUis = result.sources.map { source ->
                                SourceUi(
                                    title = source.title,
                                    relevancePercent = (source.relevanceScore * 100).toInt(),
                                )
                            }
                            updateLastMessage(messageId) { msg ->
                                msg.copy(
                                    answer = result.fullText,
                                    sources = sourceUis,
                                    isLoading = false,
                                    pipelineState = null,
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    pipelineState = null,
                                    errorMessage = null,
                                )
                            }
                            // Persist completed message to history
                            persistMessage(
                                id = messageId,
                                profileId = profileId,
                                question = question,
                                answer = result.fullText,
                                sources = result.sources,
                                timestamp = timestamp,
                            )
                        }

                        is ExplanationResult.Error -> {
                            timeoutJob?.cancel()
                            val isRetrievalMiss = result.message.startsWith(RETRIEVAL_MISS_PREFIX)

                            if (isRetrievalMiss) {
                                // Show retrieval miss as a regular assistant message to avoid
                                // stacking message-level error plus global error banner.
                                updateLastMessage(messageId) { msg ->
                                    msg.copy(
                                        answer = result.message,
                                        isLoading = false,
                                        pipelineState = null,
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        pipelineState = null,
                                        errorMessage = null,
                                    )
                                }
                            } else {
                                updateLastMessage(messageId) { msg ->
                                    msg.copy(
                                        answer = null,
                                        isLoading = false,
                                        pipelineState = PipelineState.ERROR,
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        pipelineState = PipelineState.ERROR,
                                        errorMessage = result.message,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    /** Loads chat history from the local store for the active profile. */
    fun loadHistory() {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            when (val result = chatHistoryRepository.getHistory(profileId)) {
                is EzansiResult.Success -> {
                    val historyMessages = result.data.map { chatMessage ->
                        ChatMessageUi(
                            id = chatMessage.id,
                            question = chatMessage.question,
                            answer = chatMessage.answer,
                            sources = emptyList(), // Source details not stored in history
                            isLoading = false,
                            pipelineState = null,
                            timestamp = chatMessage.timestamp,
                        )
                    }
                    _uiState.update { it.copy(messages = historyMessages) }
                }
                is EzansiResult.Error -> {
                    // History load failure is non-fatal — start with empty chat
                }
                is EzansiResult.Loading -> { /* No-op for suspending function */ }
            }
        }
    }

    /** Clears all chat history for the active profile. */
    fun onClearHistory() {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            chatHistoryRepository.clearHistory(profileId)
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    /** Dismisses the current error message. */
    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun loadActiveProfile() {
        viewModelScope.launch {
            when (val result = profileRepository.getActiveProfile()) {
                is EzansiResult.Success -> {
                    val profile = result.data
                    _uiState.update {
                        it.copy(
                            activeProfileName = profile?.name,
                            activeProfileId = profile?.id,
                        )
                    }
                    // Load history once we have the profile
                    if (profile != null) {
                        loadHistory()
                    }
                }
                is EzansiResult.Error -> {
                    // Profile load failure — the app should redirect to profile creation
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    private fun checkContentPacks() {
        viewModelScope.launch {
            when (val result = contentPackRepository.getInstalledPacks()) {
                is EzansiResult.Success -> {
                    _uiState.update { it.copy(hasContentPacks = result.data.isNotEmpty()) }
                }
                is EzansiResult.Error -> {
                    _uiState.update { it.copy(hasContentPacks = false) }
                }
                is EzansiResult.Loading -> { /* No-op */ }
            }
        }
    }

    /**
     * Updates a specific message in the list by ID.
     * Uses copy-on-write to preserve immutability of the state.
     */
    private fun updateLastMessage(
        messageId: String,
        transform: (ChatMessageUi) -> ChatMessageUi,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) transform(msg) else msg
                },
            )
        }
    }

    /** Updates the pipeline state on both the message and the global state. */
    private fun updatePipelineState(messageId: String, pipelineState: PipelineState) {
        updateLastMessage(messageId) { msg ->
            msg.copy(pipelineState = pipelineState)
        }
        _uiState.update { it.copy(pipelineState = pipelineState) }
    }

    /**
     * Persists a completed message to the chat history store.
     * Failure is non-fatal — the message is still visible in the current session.
     */
    private suspend fun persistMessage(
        id: String,
        profileId: String,
        question: String,
        answer: String,
        sources: List<ContentSource>,
        timestamp: Long,
    ) {
        chatHistoryRepository.saveMessage(
            ChatMessage(
                id = id,
                profileId = profileId,
                question = question,
                answer = answer,
                sources = sources.map { it.chunkId },
                timestamp = timestamp,
            ),
        )
    }
}

/**
 * Factory for creating [ChatViewModel] with manual dependency injection.
 *
 * Takes individual interfaces rather than AppContainer to avoid a circular
 * module dependency (feature:chat cannot depend on :app). The caller in
 * the NavHost constructs this factory from the AppContainer.
 *
 * Used with [ViewModelProvider] in the Composable via `viewModel(factory = ...)`.
 */
class ChatViewModelFactory(
    private val explanationEngine: ExplanationEngine,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val profileRepository: ProfileRepository,
    private val contentPackRepository: ContentPackRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                explanationEngine = explanationEngine,
                chatHistoryRepository = chatHistoryRepository,
                profileRepository = profileRepository,
                contentPackRepository = contentPackRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
