package com.ezansi.app.core.data.contentpack

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.ezansi.app.core.data.ContentChunk
import com.ezansi.app.core.data.PackMetadata
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Read-only wrapper around a verified content pack SQLite database.
 *
 * Provides typed access to the four pack tables (manifest, chunks,
 * embeddings, faiss_indexes) without exposing raw cursors to callers.
 *
 * This class is [Closeable] — callers must close it when done to release
 * the SQLite file handle. [PackManager] tracks open instances for cleanup.
 *
 * All methods are synchronous and must be called from a background thread
 * (typically via [DispatcherProvider.io]).
 *
 * @param packFile The verified .pack file to open.
 * @param packId The pack identifier (from manifest or filename).
 * @throws IllegalStateException if the file cannot be opened as SQLite.
 */
class PackDatabase(
    private val packFile: File,
    val packId: String,
) : Closeable {

    companion object {
        private const val TAG = "PackDatabase"

        /** Number of bytes per float32 value in embedding BLOBs. */
        private const val BYTES_PER_FLOAT = 4
    }

    private val database: SQLiteDatabase = SQLiteDatabase.openDatabase(
        packFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    )

    /**
     * Reads all key-value pairs from the manifest table.
     *
     * @return Map of manifest keys to their string values.
     */
    fun getManifest(): Map<String, String> {
        val manifest = mutableMapOf<String, String>()
        database.rawQuery("SELECT key, value FROM manifest", null).use { cursor ->
            while (cursor.moveToNext()) {
                manifest[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return manifest
    }

    /**
     * Constructs [PackMetadata] from manifest key-value pairs and file size.
     *
     * Maps manifest keys to the data class fields expected by the
     * [ContentPackRepository] interface. Missing optional fields use
     * sensible defaults.
     */
    fun getMetadata(): PackMetadata {
        val manifest = getManifest()
        return PackMetadata(
            packId = manifest["pack_id"] ?: packId,
            displayName = buildDisplayName(manifest),
            version = manifest["version"] ?: "0.0.0",
            subject = manifest["subject"] ?: "unknown",
            grade = manifest["grade"] ?: "unknown",
            curriculum = manifest["curriculum"] ?: "unknown",
            sizeBytes = packFile.length(),
            chunkCount = manifest["chunk_count"]?.toIntOrNull() ?: countChunks(),
            locale = manifest["language"] ?: "en",
        )
    }

    /**
     * Queries content chunks, optionally filtered by topic path prefix.
     *
     * @param topicPath If non-null, only returns chunks whose topic_path
     *                  starts with this prefix. Use dot-separated paths
     *                  (e.g. "term1.fractions") for hierarchical filtering.
     * @return List of [ContentChunk] instances with default relevance scores.
     */
    fun getChunks(topicPath: String? = null): List<ContentChunk> {
        val chunks = mutableListOf<ContentChunk>()
        val query: String
        val args: Array<String>?

        if (topicPath != null) {
            // Match exact path or any path that starts with "topicPath."
            query = "SELECT chunk_id, topic_path, title, content, difficulty, term " +
                "FROM chunks WHERE topic_path = ? OR topic_path LIKE ?"
            args = arrayOf(topicPath, "$topicPath.%")
        } else {
            query = "SELECT chunk_id, topic_path, title, content, difficulty, term FROM chunks"
            args = null
        }

        database.rawQuery(query, args).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("chunk_id")
            val pathIdx = cursor.getColumnIndexOrThrow("topic_path")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val contentIdx = cursor.getColumnIndexOrThrow("content")
            val difficultyIdx = cursor.getColumnIndexOrThrow("difficulty")
            val termIdx = cursor.getColumnIndexOrThrow("term")

            while (cursor.moveToNext()) {
                chunks.add(
                    ContentChunk(
                        chunkId = cursor.getString(idIdx),
                        packId = packId,
                        title = cursor.getString(titleIdx),
                        topicPath = cursor.getString(pathIdx),
                        content = cursor.getString(contentIdx),
                        difficulty = cursor.getString(difficultyIdx),
                        term = cursor.getInt(termIdx),
                    ),
                )
            }
        }
        return chunks
    }

    /**
     * Retrieves a single content chunk by its unique identifier.
     *
     * @param chunkId The chunk_id to look up.
     * @return The matching [ContentChunk], or null if not found.
     */
    fun getChunkById(chunkId: String): ContentChunk? {
        database.rawQuery(
            "SELECT chunk_id, topic_path, title, content, difficulty, term " +
                "FROM chunks WHERE chunk_id = ?",
            arrayOf(chunkId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ContentChunk(
                chunkId = cursor.getString(cursor.getColumnIndexOrThrow("chunk_id")),
                packId = packId,
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                topicPath = cursor.getString(cursor.getColumnIndexOrThrow("topic_path")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                difficulty = cursor.getString(cursor.getColumnIndexOrThrow("difficulty")),
                term = cursor.getInt(cursor.getColumnIndexOrThrow("term")),
            )
        }
    }

    /**
     * Reads the pre-computed embedding vector for a single chunk.
     *
     * Embedding BLOBs are stored as raw little-endian float32 arrays.
     * The dimension is recorded in the `dim` column for validation.
     *
     * @param chunkId The chunk_id whose embedding to retrieve.
     * @return The embedding as a [FloatArray], or null if not found.
     */
    fun getEmbedding(chunkId: String): FloatArray? {
        database.rawQuery(
            "SELECT vector, dim FROM embeddings WHERE chunk_id = ?",
            arrayOf(chunkId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val blob = cursor.getBlob(0)
            val expectedDim = cursor.getInt(1)
            return blobToFloatArray(blob, expectedDim)
        }
    }

    /**
     * Reads all pre-computed embeddings, keyed by chunk_id.
     *
     * Used by the AI pipeline for similarity search when FAISS is not
     * available or for fallback brute-force cosine similarity.
     *
     * @return Map of chunk_id to embedding [FloatArray].
     */
    fun getAllEmbeddings(): Map<String, FloatArray> {
        val embeddings = mutableMapOf<String, FloatArray>()
        database.rawQuery(
            "SELECT chunk_id, vector, dim FROM embeddings",
            null,
        ).use { cursor ->
            val chunkIdIdx = cursor.getColumnIndexOrThrow("chunk_id")
            val vectorIdx = cursor.getColumnIndexOrThrow("vector")
            val dimIdx = cursor.getColumnIndexOrThrow("dim")

            while (cursor.moveToNext()) {
                val chunkId = cursor.getString(chunkIdIdx)
                val blob = cursor.getBlob(vectorIdx)
                val dim = cursor.getInt(dimIdx)
                val floatArray = blobToFloatArray(blob, dim)
                if (floatArray != null) {
                    embeddings[chunkId] = floatArray
                } else {
                    Log.w(TAG, "Skipping malformed embedding for chunk: $chunkId")
                }
            }
        }
        return embeddings
    }

    /**
     * Reads the serialised FAISS index bytes from the faiss_indexes table.
     *
     * The FAISS C++ API requires a file path to load an index, so callers
     * will need to write these bytes to a temporary file before loading.
     * This is expected and documented in ADR-0008.
     *
     * @return The raw FAISS index bytes, or null if no index is stored.
     */
    fun getFaissIndex(): ByteArray? {
        database.rawQuery(
            "SELECT index_data FROM faiss_indexes LIMIT 1",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getBlob(0)
        }
    }

    /**
     * Reads the chunk ordering for the FAISS index.
     *
     * The chunk_order column is a JSON array of chunk_ids that maps
     * FAISS index positions to content chunks. Position 0 in the FAISS
     * index corresponds to chunk_order[0], etc.
     *
     * @return JSON string of chunk_id ordering, or null if no index exists.
     */
    fun getFaissChunkOrder(): String? {
        database.rawQuery(
            "SELECT chunk_order FROM faiss_indexes LIMIT 1",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    /**
     * Parses unique topic_path values from chunks into a hierarchical tree.
     *
     * Topic paths are dot-separated (e.g. "term1.fractions.basics") and
     * are materialised into a tree of [TopicNode] instances for the
     * topic browser UI.
     *
     * @return List of root-level topic nodes.
     */
    fun getTopics(): List<TopicNode> {
        // Collect all topic paths with their chunk counts
        val topicCounts = mutableMapOf<String, Int>()
        database.rawQuery(
            "SELECT topic_path, COUNT(*) as cnt FROM chunks GROUP BY topic_path",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                topicCounts[cursor.getString(0)] = cursor.getInt(1)
            }
        }

        return buildTopicTree(topicCounts)
    }

    /**
     * Searches chunks by matching the query string against title and content.
     *
     * This is a simple text-based fallback search. The ai-pipeline-engineer
     * will add embedding-based semantic retrieval in a later phase.
     *
     * @param query The search terms to match.
     * @param topK Maximum number of results to return.
     * @return List of matching [ContentChunk] instances.
     */
    fun searchChunksByText(query: String, topK: Int): List<ContentChunk> {
        val chunks = mutableListOf<ContentChunk>()
        val searchPattern = "%${query.trim()}%"

        database.rawQuery(
            "SELECT chunk_id, topic_path, title, content, difficulty, term " +
                "FROM chunks WHERE title LIKE ? OR content LIKE ? LIMIT ?",
            arrayOf(searchPattern, searchPattern, topK.toString()),
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("chunk_id")
            val pathIdx = cursor.getColumnIndexOrThrow("topic_path")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val contentIdx = cursor.getColumnIndexOrThrow("content")
            val difficultyIdx = cursor.getColumnIndexOrThrow("difficulty")
            val termIdx = cursor.getColumnIndexOrThrow("term")

            while (cursor.moveToNext()) {
                chunks.add(
                    ContentChunk(
                        chunkId = cursor.getString(idIdx),
                        packId = packId,
                        title = cursor.getString(titleIdx),
                        topicPath = cursor.getString(pathIdx),
                        content = cursor.getString(contentIdx),
                        difficulty = cursor.getString(difficultyIdx),
                        term = cursor.getInt(termIdx),
                    ),
                )
            }
        }
        return chunks
    }

    override fun close() {
        try {
            if (database.isOpen) {
                database.close()
                Log.d(TAG, "Closed pack database: $packId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing pack database: $packId", e)
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Converts a raw byte BLOB into a FloatArray.
     *
     * Embedding BLOBs are stored as contiguous little-endian float32 values.
     * The expected dimension is checked against the actual BLOB size to
     * detect corruption.
     */
    private fun blobToFloatArray(blob: ByteArray, expectedDim: Int): FloatArray? {
        val actualDim = blob.size / BYTES_PER_FLOAT
        if (actualDim != expectedDim) {
            Log.w(
                TAG,
                "Embedding dimension mismatch: expected=$expectedDim, " +
                    "actual=$actualDim (blob size=${blob.size})",
            )
            return null
        }

        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(actualDim) { buffer.getFloat() }
    }

    /**
     * Counts the number of chunks in the pack by querying the database.
     * Fallback for when the manifest doesn't contain chunk_count.
     */
    private fun countChunks(): Int {
        database.rawQuery("SELECT COUNT(*) FROM chunks", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Builds a human-readable display name from manifest metadata.
     * E.g. "Grade 6 Mathematics — CAPS"
     */
    private fun buildDisplayName(manifest: Map<String, String>): String {
        val subject = manifest["subject"]?.replaceFirstChar { it.uppercase() } ?: "Content"
        val grade = manifest["grade"] ?: ""
        val curriculum = manifest["curriculum"] ?: ""

        return when {
            grade.isNotEmpty() && curriculum.isNotEmpty() ->
                "Grade $grade $subject — $curriculum"
            grade.isNotEmpty() ->
                "Grade $grade $subject"
            else -> subject
        }
    }

    /**
     * Builds a hierarchical topic tree from flat topic_path counts.
     *
     * Algorithm:
     * 1. Collect all unique path prefixes (each segment boundary)
     * 2. For each prefix, sum the chunk counts of all paths that start with it
     * 3. Build the tree recursively — children are paths one segment deeper
     */
    private fun buildTopicTree(topicCounts: Map<String, Int>): List<TopicNode> {
        // Collect all unique path prefixes at every depth
        val allPrefixes = mutableSetOf<String>()
        for (path in topicCounts.keys) {
            val segments = path.split(".")
            for (i in segments.indices) {
                allPrefixes.add(segments.subList(0, i + 1).joinToString("."))
            }
        }

        // Build nodes recursively starting from root-level prefixes
        return buildChildNodes(
            parentPrefix = "",
            allPrefixes = allPrefixes,
            topicCounts = topicCounts,
        )
    }

    /**
     * Recursively builds child [TopicNode] instances for a given parent prefix.
     */
    private fun buildChildNodes(
        parentPrefix: String,
        allPrefixes: Set<String>,
        topicCounts: Map<String, Int>,
    ): List<TopicNode> {
        // Find direct children: prefixes that are one segment deeper than parent
        val parentDepth = if (parentPrefix.isEmpty()) 0 else parentPrefix.count { it == '.' } + 1
        val directChildren = allPrefixes.filter { prefix ->
            val prefixDepth = prefix.count { it == '.' } + 1
            prefixDepth == parentDepth + 1 &&
                (parentPrefix.isEmpty() || prefix.startsWith("$parentPrefix."))
        }.sorted()

        return directChildren.map { childPrefix ->
            // Sum chunk counts for this prefix and all its descendants
            val chunkCount = topicCounts.entries
                .filter { (path, _) ->
                    path == childPrefix || path.startsWith("$childPrefix.")
                }
                .sumOf { it.value }

            val name = childPrefix.substringAfterLast(".")

            TopicNode(
                path = childPrefix,
                name = name,
                children = buildChildNodes(childPrefix, allPrefixes, topicCounts),
                chunkCount = chunkCount,
            )
        }
    }
}
