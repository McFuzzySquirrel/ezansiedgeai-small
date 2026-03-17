package com.ezansi.app.feature.chat

import com.ezansi.app.core.ai.ContentSource
import com.ezansi.app.core.ai.ExplanationEngine
import com.ezansi.app.core.ai.ExplanationResult
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentChunk
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.LearnerProfile
import com.ezansi.app.core.data.PackMetadata
import com.ezansi.app.core.data.ProfileRepository
import com.ezansi.app.core.data.chat.ChatHistoryRepository
import com.ezansi.app.core.data.chat.ChatMessage
import com.ezansi.app.core.data.contentpack.TopicNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ChatViewModel] — the main conversation orchestrator.
 *
 * Tests state management, message flow, error handling, and integration
 * with the ExplanationEngine pipeline via fake implementations.
 *
 * Uses [Dispatchers.setMain] with [UnconfinedTestDispatcher] so coroutines
 * launched in [viewModelScope] execute synchronously — no flaky timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ChatViewModel")
class ChatViewModelTest {

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

    private class FakeExplanationEngine : ExplanationEngine {
        var results: List<ExplanationResult> = listOf(
            ExplanationResult.Thinking,
            ExplanationResult.Retrieving,
            ExplanationResult.Generating("Step 1"),
            ExplanationResult.Complete(
                fullText = "Step 1: Here is the answer.",
                sources = listOf(
                    ContentSource("chunk-1", "pack-1", "Fractions Basics", 0.85f),
                ),
            ),
        )

        override fun explain(question: String, profileId: String): Flow<ExplanationResult> = flow {
            for (result in results) {
                emit(result)
            }
        }
    }

    private class FakeChatHistoryRepository : ChatHistoryRepository {
        val savedMessages = mutableListOf<ChatMessage>()
        var history: List<ChatMessage> = emptyList()
        var cleared = false

        override suspend fun getHistory(profileId: String, limit: Int) =
            EzansiResult.Success(history)

        override suspend fun saveMessage(message: ChatMessage): EzansiResult<Unit> {
            savedMessages.add(message)
            return EzansiResult.Success(Unit)
        }

        override suspend fun clearHistory(profileId: String): EzansiResult<Unit> {
            cleared = true
            return EzansiResult.Success(Unit)
        }
    }

    private class FakeProfileRepository : ProfileRepository {
        var activeProfile: LearnerProfile? = null

        override suspend fun getProfiles() =
            EzansiResult.Success(listOfNotNull(activeProfile))

        override suspend fun createProfile(name: String) =
            EzansiResult.Success(LearnerProfile("new-id", name, 0, 0))

        override suspend fun getActiveProfile() =
            EzansiResult.Success(activeProfile)

        override suspend fun setActiveProfile(id: String) =
            EzansiResult.Success(Unit)

        override suspend fun deleteProfile(id: String) =
            EzansiResult.Success(Unit)
    }

    private class FakeContentPackRepository : ContentPackRepository {
        var packs: List<PackMetadata> = emptyList()

        override suspend fun getInstalledPacks() =
            EzansiResult.Success(packs)

        override suspend fun loadPack(path: String) =
            EzansiResult.Error("not implemented")

        override suspend fun verifyPack(path: String) =
            EzansiResult.Success(true)

        override suspend fun getPackMetadata(packId: String) =
            EzansiResult.Error("not found")

        override suspend fun queryChunks(packId: String, query: String, topK: Int) =
            EzansiResult.Success(emptyList<ContentChunk>())

        override suspend fun getTopicsForPack(packId: String) =
            EzansiResult.Success(emptyList<TopicNode>())
    }

    // ── Helper factories ────────────────────────────────────────────

    private fun createViewModel(
        explanationEngine: FakeExplanationEngine = FakeExplanationEngine(),
        chatHistoryRepository: FakeChatHistoryRepository = FakeChatHistoryRepository(),
        profileRepository: FakeProfileRepository = FakeProfileRepository(),
        contentPackRepository: FakeContentPackRepository = FakeContentPackRepository(),
    ): ChatViewModel = ChatViewModel(
        explanationEngine = explanationEngine,
        chatHistoryRepository = chatHistoryRepository,
        profileRepository = profileRepository,
        contentPackRepository = contentPackRepository,
    )

    private fun createViewModelWithProfile(
        explanationEngine: FakeExplanationEngine = FakeExplanationEngine(),
        chatHistoryRepository: FakeChatHistoryRepository = FakeChatHistoryRepository(),
    ): Pair<ChatViewModel, FakeChatHistoryRepository> {
        val profileRepo = FakeProfileRepository().apply {
            activeProfile = LearnerProfile(
                id = "profile-1",
                name = "Thandi",
                createdAt = 1000L,
                lastActiveAt = 1000L,
            )
        }
        val contentPackRepo = FakeContentPackRepository().apply {
            packs = listOf(
                PackMetadata(
                    packId = "maths-grade6-caps",
                    displayName = "Grade 6 Maths",
                    version = "1.0.0",
                    subject = "Mathematics",
                    grade = "6",
                    curriculum = "CAPS",
                    sizeBytes = 1024,
                    chunkCount = 100,
                    locale = "en-ZA",
                ),
            )
        }

        val vm = ChatViewModel(
            explanationEngine = explanationEngine,
            chatHistoryRepository = chatHistoryRepository,
            profileRepository = profileRepo,
            contentPackRepository = contentPackRepo,
        )
        return vm to chatHistoryRepository
    }

    // ── Initial state ───────────────────────────────────────────────

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        @DisplayName("starts with empty input text")
        fun emptyInputText() {
            val vm = createViewModel()
            assertEquals("", vm.uiState.value.inputText)
        }

        @Test
        @DisplayName("starts with no messages")
        fun noMessages() {
            val vm = createViewModel()
            assertTrue(vm.uiState.value.messages.isEmpty())
        }

        @Test
        @DisplayName("starts not generating")
        fun notGenerating() {
            val vm = createViewModel()
            assertFalse(vm.uiState.value.isGenerating)
        }

        @Test
        @DisplayName("starts with no error message")
        fun noError() {
            val vm = createViewModel()
            assertNull(vm.uiState.value.errorMessage)
        }

        @Test
        @DisplayName("no pipeline state initially")
        fun noPipelineState() {
            val vm = createViewModel()
            assertNull(vm.uiState.value.pipelineState)
        }
    }

    // ── Profile loading ─────────────────────────────────────────────

    @Nested
    @DisplayName("Profile loading")
    inner class ProfileLoadingTests {

        @Test
        @DisplayName("loads active profile on creation")
        fun loadsActiveProfile() {
            val (vm, _) = createViewModelWithProfile()
            assertEquals("Thandi", vm.uiState.value.activeProfileName)
            assertEquals("profile-1", vm.uiState.value.activeProfileId)
        }

        @Test
        @DisplayName("no active profile leaves name null")
        fun noActiveProfile() {
            val vm = createViewModel()
            assertNull(vm.uiState.value.activeProfileName)
            assertNull(vm.uiState.value.activeProfileId)
        }
    }

    // ── Input handling ──────────────────────────────────────────────

    @Nested
    @DisplayName("Input handling")
    inner class InputHandlingTests {

        @Test
        @DisplayName("onInputChanged updates inputText")
        fun inputChangedUpdatesState() {
            val vm = createViewModel()
            vm.onInputChanged("What are fractions?")
            assertEquals("What are fractions?", vm.uiState.value.inputText)
        }

        @Test
        @DisplayName("blank input is ignored by onSendMessage")
        fun blankInputIgnored() {
            val (vm, _) = createViewModelWithProfile()
            vm.onInputChanged("   ")
            vm.onSendMessage()

            // No message should be added
            assertTrue(vm.uiState.value.messages.isEmpty())
        }

        @Test
        @DisplayName("send clears input text")
        fun sendClearsInputText() {
            val (vm, _) = createViewModelWithProfile()
            vm.onInputChanged("What are fractions?")
            vm.onSendMessage()

            assertEquals("", vm.uiState.value.inputText)
        }
    }

    // ── Message sending ─────────────────────────────────────────────

    @Nested
    @DisplayName("Message sending and pipeline")
    inner class MessageSendingTests {

        @Test
        @DisplayName("send message adds message and completes pipeline")
        fun sendMessageCompletesPipeline() {
            val (vm, _) = createViewModelWithProfile()
            vm.onInputChanged("What are fractions?")
            vm.onSendMessage()

            val state = vm.uiState.value
            assertEquals(1, state.messages.size)
            val msg = state.messages[0]
            assertEquals("What are fractions?", msg.question)
            assertNotNull(msg.answer)
            assertFalse(msg.isLoading)
            assertFalse(state.isGenerating)
        }

        @Test
        @DisplayName("completed message is persisted to history")
        fun messagePersistedToHistory() {
            val chatRepo = FakeChatHistoryRepository()
            val (vm, _) = createViewModelWithProfile(chatHistoryRepository = chatRepo)
            vm.onInputChanged("What are fractions?")
            vm.onSendMessage()

            assertEquals(1, chatRepo.savedMessages.size)
            assertEquals("What are fractions?", chatRepo.savedMessages[0].question)
        }

        @Test
        @DisplayName("no send without active profile")
        fun noSendWithoutProfile() {
            val vm = createViewModel()
            vm.onInputChanged("Hello")
            vm.onSendMessage()

            // No profile → no message sent
            assertTrue(vm.uiState.value.messages.isEmpty())
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("pipeline error sets error message")
        fun pipelineErrorSetsErrorMessage() {
            val engine = FakeExplanationEngine().apply {
                results = listOf(
                    ExplanationResult.Thinking,
                    ExplanationResult.Error("No content packs installed"),
                )
            }

            val (vm, _) = createViewModelWithProfile(explanationEngine = engine)
            vm.onInputChanged("What are fractions?")
            vm.onSendMessage()

            assertEquals("No content packs installed", vm.uiState.value.errorMessage)
            assertFalse(vm.uiState.value.isGenerating)
        }

        @Test
        @DisplayName("onDismissError clears error message")
        fun dismissErrorClearsMessage() {
            val engine = FakeExplanationEngine().apply {
                results = listOf(ExplanationResult.Error("oops"))
            }

            val (vm, _) = createViewModelWithProfile(explanationEngine = engine)
            vm.onInputChanged("test")
            vm.onSendMessage()

            assertNotNull(vm.uiState.value.errorMessage)
            vm.onDismissError()
            assertNull(vm.uiState.value.errorMessage)
        }

        @Test
        @DisplayName("flow exception sets generic error message")
        fun flowExceptionSetsGenericError() {
            val engine = object : ExplanationEngine {
                override fun explain(question: String, profileId: String) = flow<ExplanationResult> {
                    throw RuntimeException("Unexpected failure")
                }
            }

            val profileRepo = FakeProfileRepository().apply {
                activeProfile = LearnerProfile("p1", "Test", 0, 0)
            }
            val contentPackRepo = FakeContentPackRepository().apply {
                packs = listOf(
                    PackMetadata("p1", "Test", "1.0", "Math", "6", "CAPS", 100, 10, "en"),
                )
            }

            val vm = ChatViewModel(
                explanationEngine = engine,
                chatHistoryRepository = FakeChatHistoryRepository(),
                profileRepository = profileRepo,
                contentPackRepository = contentPackRepo,
            )

            vm.onInputChanged("test")
            vm.onSendMessage()

            assertNotNull(vm.uiState.value.errorMessage)
            assertFalse(vm.uiState.value.isGenerating)
        }
    }

    // ── History management ──────────────────────────────────────────

    @Nested
    @DisplayName("History management")
    inner class HistoryManagementTests {

        @Test
        @DisplayName("loadHistory populates messages from repository")
        fun loadHistoryPopulatesMessages() {
            val chatRepo = FakeChatHistoryRepository().apply {
                history = listOf(
                    ChatMessage("m1", "profile-1", "Question 1", "Answer 1", emptyList(), 1000),
                    ChatMessage("m2", "profile-1", "Question 2", "Answer 2", emptyList(), 2000),
                )
            }

            val (vm, _) = createViewModelWithProfile(chatHistoryRepository = chatRepo)

            // History is loaded automatically in init when profile is set
            val messages = vm.uiState.value.messages
            assertEquals(2, messages.size)
            assertEquals("Question 1", messages[0].question)
            assertEquals("Answer 1", messages[0].answer)
        }

        @Test
        @DisplayName("onClearHistory clears messages")
        fun clearHistoryClearsMessages() {
            val chatRepo = FakeChatHistoryRepository().apply {
                history = listOf(
                    ChatMessage("m1", "profile-1", "Q", "A", emptyList(), 1000),
                )
            }
            val (vm, _) = createViewModelWithProfile(chatHistoryRepository = chatRepo)

            assertTrue(vm.uiState.value.messages.isNotEmpty())
            vm.onClearHistory()

            assertTrue(vm.uiState.value.messages.isEmpty())
            assertTrue(chatRepo.cleared)
        }
    }

    // ── Content pack detection ──────────────────────────────────────

    @Nested
    @DisplayName("Content pack detection")
    inner class ContentPackTests {

        @Test
        @DisplayName("no content packs sets hasContentPacks to false")
        fun noContentPacks() {
            val vm = createViewModel(
                contentPackRepository = FakeContentPackRepository().apply { packs = emptyList() },
            )
            assertFalse(vm.uiState.value.hasContentPacks)
        }

        @Test
        @DisplayName("installed packs sets hasContentPacks to true")
        fun hasContentPacks() {
            val contentPackRepo = FakeContentPackRepository().apply {
                packs = listOf(
                    PackMetadata("p1", "Test", "1.0", "Math", "6", "CAPS", 100, 10, "en"),
                )
            }
            val vm = createViewModel(contentPackRepository = contentPackRepo)
            assertTrue(vm.uiState.value.hasContentPacks)
        }
    }
}
