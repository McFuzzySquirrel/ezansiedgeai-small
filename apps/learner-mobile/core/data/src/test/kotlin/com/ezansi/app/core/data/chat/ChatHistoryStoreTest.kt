package com.ezansi.app.core.data.chat

import com.ezansi.app.core.data.FakeAndroidKeyStoreSpi
import com.ezansi.app.core.data.encryption.ProfileEncryption
import com.ezansi.app.core.data.installFakeAndroidKeyStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import org.robolectric.junit5.RobolectricExtension
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ChatHistoryStore] — encrypted SQLite chat storage.
 *
 * Tests the complete message lifecycle: save → retrieve → clear, with
 * encrypted fields, chronological ordering, per-profile isolation,
 * and the 100-message-per-profile history limit.
 *
 * Uses Robolectric for SQLiteDatabase + fake AndroidKeyStore for
 * ProfileEncryption's AES-256-GCM operations.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
@DisplayName("ChatHistoryStore")
class ChatHistoryStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var encryption: ProfileEncryption
    private lateinit var store: ChatHistoryStore

    @BeforeEach
    fun setUp() {
        installFakeAndroidKeyStore()
        FakeAndroidKeyStoreSpi.reset()
        encryption = ProfileEncryption()
        store = ChatHistoryStore(tempDir, encryption)
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createMessage(
        profileId: String = "profile-1",
        question: String = "What are fractions?",
        answer: String = "A fraction represents part of a whole.",
        sources: List<String> = listOf("chunk-1", "chunk-2"),
        timestamp: Long = System.currentTimeMillis(),
    ) = ChatMessage(
        id = UUID.randomUUID().toString(),
        profileId = profileId,
        question = question,
        answer = answer,
        sources = sources,
        timestamp = timestamp,
    )

    // ── Save and retrieve ───────────────────────────────────────────

    @Nested
    @DisplayName("Save and retrieve")
    inner class SaveRetrieveTests {

        @Test
        @DisplayName("save and retrieve round-trip preserves data")
        fun roundTrip() {
            val msg = createMessage(
                question = "What are equivalent fractions?",
                answer = "Equivalent fractions have the same value.",
                sources = listOf("chunk-a", "chunk-b"),
                timestamp = 1_700_000_000_000L,
            )
            store.saveMessage(msg)

            val history = store.getHistory("profile-1", limit = 50)
            assertEquals(1, history.size)

            val loaded = history[0]
            assertEquals(msg.id, loaded.id)
            assertEquals(msg.profileId, loaded.profileId)
            assertEquals(msg.question, loaded.question)
            assertEquals(msg.answer, loaded.answer)
            assertEquals(msg.sources, loaded.sources)
            assertEquals(msg.timestamp, loaded.timestamp)
        }

        @Test
        @DisplayName("multiple messages returned in chronological order (oldest first)")
        fun chronologicalOrder() {
            store.saveMessage(createMessage(question = "First", timestamp = 1000))
            store.saveMessage(createMessage(question = "Second", timestamp = 2000))
            store.saveMessage(createMessage(question = "Third", timestamp = 3000))

            val history = store.getHistory("profile-1", limit = 50)
            assertEquals(3, history.size)
            assertEquals("First", history[0].question)
            assertEquals("Second", history[1].question)
            assertEquals("Third", history[2].question)
        }

        @Test
        @DisplayName("limit parameter restricts result count")
        fun limitParameter() {
            for (i in 1..10) {
                store.saveMessage(createMessage(question = "Q$i", timestamp = i.toLong()))
            }

            val history = store.getHistory("profile-1", limit = 3)
            assertEquals(3, history.size)
            // Should return the 3 most recent, in chronological order
            assertEquals("Q8", history[0].question)
            assertEquals("Q9", history[1].question)
            assertEquals("Q10", history[2].question)
        }

        @Test
        @DisplayName("empty history returns empty list")
        fun emptyHistory() {
            val history = store.getHistory("profile-1", limit = 50)
            assertTrue(history.isEmpty())
        }
    }

    // ── Profile isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Profile isolation")
    inner class IsolationTests {

        @Test
        @DisplayName("messages are isolated per profile")
        fun messagesIsolatedPerProfile() {
            store.saveMessage(
                createMessage(profileId = "alice", question = "Alice's question", timestamp = 1000),
            )
            store.saveMessage(
                createMessage(profileId = "bob", question = "Bob's question", timestamp = 2000),
            )

            val aliceHistory = store.getHistory("alice", limit = 50)
            val bobHistory = store.getHistory("bob", limit = 50)

            assertEquals(1, aliceHistory.size)
            assertEquals("Alice's question", aliceHistory[0].question)

            assertEquals(1, bobHistory.size)
            assertEquals("Bob's question", bobHistory[0].question)
        }
    }

    // ── History limit ───────────────────────────────────────────────

    @Nested
    @DisplayName("History limit (100 messages)")
    inner class HistoryLimitTests {

        @Test
        @DisplayName("history is trimmed to 100 messages per profile")
        fun trimTo100() {
            // Insert 105 messages
            for (i in 1..105) {
                store.saveMessage(
                    createMessage(question = "Q$i", timestamp = i.toLong()),
                )
            }

            val history = store.getHistory("profile-1", limit = 200) // Request more than max
            assertTrue(
                history.size <= 100,
                "Expected ≤ 100 messages after trim, got ${history.size}",
            )

            // The oldest messages should have been trimmed
            if (history.isNotEmpty()) {
                val firstQuestion = history[0].question
                // First 5 should be trimmed, so first available should be Q6 or later
                assertTrue(
                    firstQuestion != "Q1",
                    "Oldest messages should be trimmed",
                )
            }
        }
    }

    // ── Clear history ───────────────────────────────────────────────

    @Nested
    @DisplayName("Clear history")
    inner class ClearTests {

        @Test
        @DisplayName("clearHistory removes all messages for profile")
        fun clearRemovesAll() {
            store.saveMessage(createMessage(timestamp = 1000))
            store.saveMessage(createMessage(timestamp = 2000))

            store.clearHistory("profile-1")

            val history = store.getHistory("profile-1", limit = 50)
            assertTrue(history.isEmpty())
        }

        @Test
        @DisplayName("clearHistory only affects target profile")
        fun clearOnlyTargetProfile() {
            store.saveMessage(
                createMessage(profileId = "alice", question = "Alice's Q", timestamp = 1000),
            )
            store.saveMessage(
                createMessage(profileId = "bob", question = "Bob's Q", timestamp = 2000),
            )

            store.clearHistory("alice")

            val aliceHistory = store.getHistory("alice", limit = 50)
            val bobHistory = store.getHistory("bob", limit = 50)

            assertTrue(aliceHistory.isEmpty())
            assertEquals(1, bobHistory.size)
        }
    }

    // ── Encryption ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Field encryption")
    inner class EncryptionTests {

        @Test
        @DisplayName("encrypted fields are decrypted on retrieval")
        fun encryptedFieldsDecrypted() {
            val msg = createMessage(
                question = "Test encryption round-trip",
                answer = "This answer should be encrypted then decrypted",
            )
            store.saveMessage(msg)

            val loaded = store.getHistory("profile-1", limit = 1)
            assertEquals(1, loaded.size)
            assertEquals("Test encryption round-trip", loaded[0].question)
            assertEquals("This answer should be encrypted then decrypted", loaded[0].answer)
        }
    }
}
