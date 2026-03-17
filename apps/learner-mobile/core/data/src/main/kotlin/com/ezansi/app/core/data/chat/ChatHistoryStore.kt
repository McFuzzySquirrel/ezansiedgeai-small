package com.ezansi.app.core.data.chat

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Log
import com.ezansi.app.core.data.encryption.ProfileEncryption
import java.io.File

/**
 * SQLite-based storage for encrypted chat history.
 *
 * Uses raw [SQLiteDatabase] with WAL mode for concurrent reads and
 * crash-safe writes. Sensitive fields (question, answer, sources)
 * are encrypted with AES-256-GCM and stored as Base64-encoded strings.
 *
 * The database file (`chat_history.db`) is stored in the profiles
 * directory on internal storage — never on removable media.
 *
 * **No learner data is logged** — only structural messages (row counts,
 * operation success/failure).
 */
class ChatHistoryStore(
    private val profilesDir: File,
    private val encryption: ProfileEncryption,
) {

    companion object {
        private const val DB_NAME = "chat_history.db"
        private const val TABLE = "messages"
        private const val MAX_MESSAGES_PER_PROFILE = 100
        private const val TAG = "ChatHistoryStore"
    }

    /**
     * Lazily opened SQLite database with WAL mode and schema initialisation.
     *
     * WAL (Write-Ahead Logging) mode provides:
     * - Concurrent readers don't block writers
     * - Crash recovery — incomplete transactions are rolled back
     * - Better performance for the read-heavy chat history pattern
     */
    private val db: SQLiteDatabase by lazy {
        val dbFile = File(profilesDir, DB_NAME)
        val database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        database.enableWriteAheadLogging()
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE (" +
                "id TEXT PRIMARY KEY, " +
                "profile_id TEXT NOT NULL, " +
                "question TEXT NOT NULL, " +
                "answer TEXT NOT NULL, " +
                "sources TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL" +
                ")",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_profile_timestamp " +
                "ON $TABLE (profile_id, timestamp)",
        )
        Log.d(TAG, "Chat history database initialised")
        database
    }

    /**
     * Saves a [message] with encrypted content fields.
     * Trims history to [MAX_MESSAGES_PER_PROFILE] after insert.
     */
    fun saveMessage(message: ChatMessage) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("profile_id", message.profileId)
            put("question", encryptField(message.question))
            put("answer", encryptField(message.answer))
            put("sources", encryptField(message.sources.joinToString("|")))
            put("timestamp", message.timestamp)
        }
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trimHistory(message.profileId)
    }

    /**
     * Returns the most recent [limit] messages for the given profile,
     * in chronological order (oldest first).
     *
     * Corrupt rows are skipped with an error log — never crashes.
     */
    fun getHistory(profileId: String, limit: Int): List<ChatMessage> {
        val cursor = db.query(
            TABLE,
            null,
            "profile_id = ?",
            arrayOf(profileId),
            null,
            null,
            "timestamp DESC",
            limit.toString(),
        )

        val messages = mutableListOf<ChatMessage>()
        cursor.use { c ->
            while (c.moveToNext()) {
                try {
                    val sourcesRaw = decryptField(
                        c.getString(c.getColumnIndexOrThrow("sources")),
                    )
                    messages.add(
                        ChatMessage(
                            id = c.getString(c.getColumnIndexOrThrow("id")),
                            profileId = c.getString(c.getColumnIndexOrThrow("profile_id")),
                            question = decryptField(
                                c.getString(c.getColumnIndexOrThrow("question")),
                            ),
                            answer = decryptField(
                                c.getString(c.getColumnIndexOrThrow("answer")),
                            ),
                            sources = sourcesRaw
                                .split("|")
                                .filter { it.isNotEmpty() },
                            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        ),
                    )
                } catch (e: Exception) {
                    // Skip corrupt rows — log structural info only
                    Log.e(TAG, "Skipping corrupt message row: ${e.javaClass.simpleName}")
                }
            }
        }

        return messages.reversed() // Chronological order (oldest first)
    }

    /**
     * Deletes all messages for the given profile.
     */
    fun clearHistory(profileId: String) {
        val deleted = db.delete(TABLE, "profile_id = ?", arrayOf(profileId))
        Log.d(TAG, "Cleared $deleted message(s) for profile")
    }

    /**
     * Closes the database connection. Called during cleanup.
     */
    fun close() {
        if (db.isOpen) {
            db.close()
        }
    }

    /**
     * Trims history for a profile to the most recent [MAX_MESSAGES_PER_PROFILE].
     * Called after every insert to keep storage bounded.
     */
    private fun trimHistory(profileId: String) {
        db.execSQL(
            "DELETE FROM $TABLE WHERE profile_id = ? AND id NOT IN " +
                "(SELECT id FROM $TABLE WHERE profile_id = ? " +
                "ORDER BY timestamp DESC LIMIT $MAX_MESSAGES_PER_PROFILE)",
            arrayOf(profileId, profileId),
        )
    }

    /** Encrypts a plaintext field and returns Base64-encoded ciphertext. */
    private fun encryptField(plaintext: String): String {
        val encrypted = encryption.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /** Decodes Base64 and decrypts a field back to plaintext. */
    private fun decryptField(encoded: String): String {
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        return String(encryption.decrypt(encrypted), Charsets.UTF_8)
    }
}
