package com.ezansi.app.core.data.chat

import com.ezansi.app.core.common.EzansiResult

/**
 * Repository for persisting chat interactions per learner profile.
 *
 * Chat history is automatically persisted on every interaction (no "save"
 * button) and survives app kills and phone restarts (PRD §8.10 LC-01, LC-02).
 * Each profile's history is isolated and limited to the most recent 100
 * messages to control storage growth.
 *
 * Implementors must ensure:
 * - Message content (question, answer, sources) is encrypted at rest
 * - History is deleted when the owning profile is deleted
 * - No learner data is logged, even in debug builds
 *
 * @see ChatMessage for the message data structure
 */
interface ChatHistoryRepository {

    /**
     * Returns the most recent messages for the given profile.
     *
     * @param profileId The profile whose history to retrieve.
     * @param limit Maximum number of messages to return (default 50).
     * @return Messages in chronological order (oldest first).
     */
    suspend fun getHistory(
        profileId: String,
        limit: Int = 50,
    ): EzansiResult<List<ChatMessage>>

    /**
     * Persists a chat message immediately. Auto-trims history
     * to the most recent 100 messages per profile.
     *
     * @param message The message to save.
     */
    suspend fun saveMessage(message: ChatMessage): EzansiResult<Unit>

    /**
     * Deletes all chat history for the given profile.
     * Called as part of profile deletion cascade.
     *
     * @param profileId The profile whose history to clear.
     */
    suspend fun clearHistory(profileId: String): EzansiResult<Unit>
}
