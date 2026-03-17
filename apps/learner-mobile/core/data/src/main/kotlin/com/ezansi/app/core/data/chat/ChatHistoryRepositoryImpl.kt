package com.ezansi.app.core.data.chat

import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.EzansiResult
import kotlinx.coroutines.withContext

/**
 * Production implementation of [ChatHistoryRepository].
 *
 * Delegates to [ChatHistoryStore] for SQLite operations with field-level
 * AES-256-GCM encryption. All database access runs on [DispatcherProvider.io]
 * to avoid blocking the UI thread.
 */
class ChatHistoryRepositoryImpl(
    private val chatHistoryStore: ChatHistoryStore,
    private val dispatcherProvider: DispatcherProvider,
) : ChatHistoryRepository {

    override suspend fun getHistory(
        profileId: String,
        limit: Int,
    ): EzansiResult<List<ChatMessage>> =
        withContext(dispatcherProvider.io) {
            try {
                val messages = chatHistoryStore.getHistory(profileId, limit)
                EzansiResult.Success(messages)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to load chat history", e)
            }
        }

    override suspend fun saveMessage(message: ChatMessage): EzansiResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                chatHistoryStore.saveMessage(message)
                EzansiResult.Success(Unit)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to save message", e)
            }
        }

    override suspend fun clearHistory(profileId: String): EzansiResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                chatHistoryStore.clearHistory(profileId)
                EzansiResult.Success(Unit)
            } catch (e: Exception) {
                EzansiResult.Error("Failed to clear chat history", e)
            }
        }
}
