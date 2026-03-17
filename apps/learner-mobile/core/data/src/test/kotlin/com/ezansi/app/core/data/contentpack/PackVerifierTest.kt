package com.ezansi.app.core.data.contentpack

import android.database.sqlite.SQLiteDatabase
import com.ezansi.app.core.common.EzansiResult
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
import java.security.MessageDigest
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [PackVerifier] — the 5-step content pack integrity checker.
 *
 * Each test creates a real SQLite database file (via Robolectric's shadow)
 * and runs the full verification pipeline. No network, no mocks — real
 * I/O against temp files that are cleaned up automatically.
 *
 * Verification steps tested:
 * 1. File existence and readability
 * 2. Valid SQLite format
 * 3. Required tables: manifest, chunks, embeddings
 * 4. Schema version ≤ MAX_SUPPORTED (1)
 * 5. SHA-256 checksums on every chunk
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
@DisplayName("PackVerifier")
class PackVerifierTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var verifier: PackVerifier

    @BeforeEach
    fun setUp() {
        verifier = PackVerifier()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Computes SHA-256 hex of a string, matching PackVerifier's internal method. */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates a valid content pack SQLite database with the correct schema.
     *
     * @param chunks List of (chunkId, content) pairs to insert.
     * @param schemaVersion Version string stored in the manifest.
     * @param corruptChecksum If true, the first chunk gets a wrong SHA-256.
     */
    private fun createValidPack(
        chunks: List<Pair<String, String>> = listOf(
            "chunk-1" to "A fraction represents part of a whole.",
            "chunk-2" to "To add fractions, find a common denominator.",
        ),
        schemaVersion: String = "1",
        corruptChecksum: Boolean = false,
    ): File {
        val packFile = File(tempDir, "test.pack")
        val db = SQLiteDatabase.openOrCreateDatabase(packFile, null)
        try {
            // Create required tables
            db.execSQL("CREATE TABLE manifest (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL(
                "CREATE TABLE chunks (" +
                    "chunk_id TEXT PRIMARY KEY, " +
                    "content TEXT NOT NULL, " +
                    "sha256 TEXT NOT NULL, " +
                    "topic_path TEXT DEFAULT '', " +
                    "title TEXT DEFAULT '')",
            )
            db.execSQL(
                "CREATE TABLE embeddings (" +
                    "chunk_id TEXT PRIMARY KEY, " +
                    "embedding BLOB)",
            )

            // Insert manifest
            db.execSQL(
                "INSERT INTO manifest (key, value) VALUES ('schema_version', ?)",
                arrayOf(schemaVersion),
            )
            db.execSQL(
                "INSERT INTO manifest (key, value) VALUES ('pack_id', 'test-pack')",
            )

            // Insert chunks with correct (or corrupted) checksums
            chunks.forEachIndexed { index, (chunkId, content) ->
                val hash = if (corruptChecksum && index == 0) {
                    "0000000000000000000000000000000000000000000000000000000000000000"
                } else {
                    sha256(content)
                }
                db.execSQL(
                    "INSERT INTO chunks (chunk_id, content, sha256) VALUES (?, ?, ?)",
                    arrayOf(chunkId, content, hash),
                )
            }
        } finally {
            db.close()
        }
        return packFile
    }

    // ── Happy path ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid pack")
    inner class ValidPackTests {

        @Test
        @DisplayName("valid pack passes all verification steps")
        fun validPackPasses() {
            val packFile = createValidPack()
            val result = verifier.verify(packFile)

            assertIs<EzansiResult.Success<Boolean>>(result)
            assertTrue(result.data)
        }

        @Test
        @DisplayName("pack with single chunk passes")
        fun singleChunkPasses() {
            val packFile = createValidPack(
                chunks = listOf("only-chunk" to "Content here"),
            )
            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Success<Boolean>>(result)
        }
    }

    // ── Step 1: File existence ──────────────────────────────────────

    @Nested
    @DisplayName("Step 1: File existence")
    inner class FileExistenceTests {

        @Test
        @DisplayName("non-existent file → Error")
        fun nonExistentFile() {
            val missing = File(tempDir, "does_not_exist.pack")
            val result = verifier.verify(missing)

            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("not found", ignoreCase = true))
        }
    }

    // ── Step 2: Valid SQLite ────────────────────────────────────────

    @Nested
    @DisplayName("Step 2: Valid SQLite format")
    inner class ValidSqliteTests {

        @Test
        @DisplayName("non-SQLite file → Error")
        fun nonSqliteFile() {
            val badFile = File(tempDir, "not_sqlite.pack")
            badFile.writeText("This is not a SQLite database at all!")

            val result = verifier.verify(badFile)
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("Not a valid", ignoreCase = true))
        }

        @Test
        @DisplayName("empty file → Error")
        fun emptyFile() {
            val emptyFile = File(tempDir, "empty.pack")
            emptyFile.createNewFile()

            val result = verifier.verify(emptyFile)
            assertIs<EzansiResult.Error>(result)
        }
    }

    // ── Step 3: Required tables ─────────────────────────────────────

    @Nested
    @DisplayName("Step 3: Required tables")
    inner class RequiredTablesTests {

        @Test
        @DisplayName("missing chunks table → Error")
        fun missingChunksTable() {
            val packFile = File(tempDir, "no_chunks.pack")
            val db = SQLiteDatabase.openOrCreateDatabase(packFile, null)
            db.execSQL("CREATE TABLE manifest (key TEXT, value TEXT)")
            db.execSQL("CREATE TABLE embeddings (chunk_id TEXT)")
            // No 'chunks' table
            db.close()

            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("Not a valid", ignoreCase = true))
        }

        @Test
        @DisplayName("missing embeddings table → Error")
        fun missingEmbeddingsTable() {
            val packFile = File(tempDir, "no_embeddings.pack")
            val db = SQLiteDatabase.openOrCreateDatabase(packFile, null)
            db.execSQL("CREATE TABLE manifest (key TEXT, value TEXT)")
            db.execSQL("CREATE TABLE chunks (chunk_id TEXT, content TEXT, sha256 TEXT)")
            // No 'embeddings' table
            db.close()

            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Error>(result)
        }

        @Test
        @DisplayName("missing manifest table → Error")
        fun missingManifestTable() {
            val packFile = File(tempDir, "no_manifest.pack")
            val db = SQLiteDatabase.openOrCreateDatabase(packFile, null)
            db.execSQL("CREATE TABLE chunks (chunk_id TEXT, content TEXT, sha256 TEXT)")
            db.execSQL("CREATE TABLE embeddings (chunk_id TEXT)")
            // No 'manifest' table
            db.close()

            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Error>(result)
        }
    }

    // ── Step 4: Schema version ──────────────────────────────────────

    @Nested
    @DisplayName("Step 4: Schema version")
    inner class SchemaVersionTests {

        @Test
        @DisplayName("schema version 1 is accepted")
        fun version1Accepted() {
            val packFile = createValidPack(schemaVersion = "1")
            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Success<Boolean>>(result)
        }

        @Test
        @DisplayName("schema version 999 is rejected (too new)")
        fun versionTooNew() {
            val packFile = createValidPack(schemaVersion = "999")
            val result = verifier.verify(packFile)

            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("Incompatible", ignoreCase = true))
        }

        @Test
        @DisplayName("non-numeric schema version → Error")
        fun nonNumericVersion() {
            val packFile = createValidPack(schemaVersion = "beta")
            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Error>(result)
        }
    }

    // ── Step 5: SHA-256 checksums ───────────────────────────────────

    @Nested
    @DisplayName("Step 5: SHA-256 checksums")
    inner class ChecksumTests {

        @Test
        @DisplayName("corrupted checksum → Error")
        fun corruptedChecksum() {
            val packFile = createValidPack(corruptChecksum = true)
            val result = verifier.verify(packFile)

            assertIs<EzansiResult.Error>(result)
            assertTrue(result.message.contains("corrupted", ignoreCase = true))
        }

        @Test
        @DisplayName("empty chunks table → Error")
        fun emptyChunks() {
            val packFile = createValidPack(chunks = emptyList())
            val result = verifier.verify(packFile)
            assertIs<EzansiResult.Error>(result)
        }
    }
}
