package com.ezansi.app.core.data.contentpack

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.ezansi.app.core.common.EzansiResult
import java.io.File
import java.security.MessageDigest

/**
 * Verifies content pack integrity before any data enters the runtime.
 *
 * Verification steps (all must pass):
 * 1. File exists and is readable
 * 2. File is a valid SQLite database
 * 3. Required tables are present (manifest, chunks, embeddings)
 * 4. Schema version is compatible with this app version
 * 5. SHA-256 checksums in each chunk row match the actual content
 *
 * A pack that fails any step is rejected with a clear, user-facing error
 * message. No data from a failed pack is ever used.
 *
 * @see <a href="../../ejs-docs/adr/0008-content-pack-sqlite-format.md">ADR-0008</a>
 */
class PackVerifier {

    companion object {
        private const val TAG = "PackVerifier"

        /** Maximum schema version this app version can read. */
        private const val MAX_SUPPORTED_SCHEMA_VERSION = 1

        /** Tables that must exist in every valid content pack. */
        private val REQUIRED_TABLES = setOf("manifest", "chunks", "embeddings")
    }

    /**
     * Runs all verification checks on a content pack file.
     *
     * @param packFile The .pack file to verify.
     * @return [EzansiResult.Success] with `true` if all checks pass,
     *         [EzansiResult.Error] with a human-readable message on failure.
     */
    fun verify(packFile: File): EzansiResult<Boolean> {
        // Step 1: File existence and readability
        if (!packFile.exists()) {
            return EzansiResult.Error("File not found: ${packFile.name}")
        }
        if (!packFile.canRead()) {
            return EzansiResult.Error("Cannot read file: ${packFile.name}")
        }

        // Step 2–5: Open the database and run structural + integrity checks
        var database: SQLiteDatabase? = null
        return try {
            database = openReadOnlyDatabase(packFile)
                ?: return EzansiResult.Error("Not a valid content pack")

            val tableCheck = verifyRequiredTablesExist(database)
            if (tableCheck is EzansiResult.Error) return tableCheck

            val versionCheck = verifySchemaVersion(database)
            if (versionCheck is EzansiResult.Error) return versionCheck

            val checksumCheck = verifyChunkChecksums(database)
            if (checksumCheck is EzansiResult.Error) return checksumCheck

            Log.i(TAG, "Pack verified successfully: ${packFile.name}")
            EzansiResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Verification failed for ${packFile.name}", e)
            EzansiResult.Error(
                "Not a valid content pack",
                cause = e,
            )
        } finally {
            closeDatabaseQuietly(database)
        }
    }

    /**
     * Attempts to open the file as a read-only SQLite database.
     * Returns null if the file is not a valid SQLite database.
     */
    private fun openReadOnlyDatabase(packFile: File): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                packFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open as SQLite: ${packFile.name}", e)
            null
        }
    }

    /**
     * Verifies that all required tables exist in the database.
     */
    private fun verifyRequiredTablesExist(database: SQLiteDatabase): EzansiResult<Boolean> {
        val existingTables = mutableSetOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                existingTables.add(cursor.getString(0))
            }
        }

        val missingTables = REQUIRED_TABLES - existingTables
        if (missingTables.isNotEmpty()) {
            Log.w(TAG, "Missing required tables: $missingTables")
            return EzansiResult.Error("Not a valid content pack")
        }

        return EzansiResult.Success(true)
    }

    /**
     * Verifies the pack's schema_version is supported by this app version.
     */
    private fun verifySchemaVersion(database: SQLiteDatabase): EzansiResult<Boolean> {
        val schemaVersion = readManifestValue(database, "schema_version")
            ?: return EzansiResult.Error("Not a valid content pack")

        val version = schemaVersion.toIntOrNull()
            ?: return EzansiResult.Error("Not a valid content pack")

        if (version > MAX_SUPPORTED_SCHEMA_VERSION) {
            return EzansiResult.Error(
                "Incompatible pack version (v$version). " +
                    "Please update the app to load this content pack.",
            )
        }

        return EzansiResult.Success(true)
    }

    /**
     * Verifies SHA-256 checksums for every content chunk.
     *
     * Each chunk row stores a `sha256` column containing the hex-encoded
     * SHA-256 hash of the `content` column (UTF-8 encoded). This method
     * recomputes the hash and compares — any mismatch means the pack is
     * corrupted or tampered with.
     */
    private fun verifyChunkChecksums(database: SQLiteDatabase): EzansiResult<Boolean> {
        database.rawQuery(
            "SELECT chunk_id, content, sha256 FROM chunks",
            null,
        ).use { cursor ->
            val chunkIdIndex = cursor.getColumnIndexOrThrow("chunk_id")
            val contentIndex = cursor.getColumnIndexOrThrow("content")
            val sha256Index = cursor.getColumnIndexOrThrow("sha256")

            var checkedCount = 0
            while (cursor.moveToNext()) {
                val chunkId = cursor.getString(chunkIdIndex)
                val content = cursor.getString(contentIndex)
                val expectedHash = cursor.getString(sha256Index)

                val actualHash = computeSha256Hex(content)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    Log.e(
                        TAG,
                        "Checksum mismatch for chunk '$chunkId': " +
                            "expected=$expectedHash, actual=$actualHash",
                    )
                    return EzansiResult.Error("Content pack is corrupted")
                }
                checkedCount++
            }

            if (checkedCount == 0) {
                Log.w(TAG, "Pack contains no content chunks")
                return EzansiResult.Error("Not a valid content pack")
            }

            Log.d(TAG, "All $checkedCount chunk checksums verified")
        }

        return EzansiResult.Success(true)
    }

    /**
     * Reads a single value from the manifest key-value table.
     */
    private fun readManifestValue(database: SQLiteDatabase, key: String): String? {
        database.rawQuery(
            "SELECT value FROM manifest WHERE key = ?",
            arrayOf(key),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    /**
     * Computes the SHA-256 hash of a string (UTF-8 encoded) and returns
     * the lowercase hex-encoded digest.
     */
    private fun computeSha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Closes a database handle without throwing — verification cleanup
     * must never mask the original verification error.
     */
    private fun closeDatabaseQuietly(database: SQLiteDatabase?) {
        try {
            database?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing database during verification", e)
        }
    }
}
