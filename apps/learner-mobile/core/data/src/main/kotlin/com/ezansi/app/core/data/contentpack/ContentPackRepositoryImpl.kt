package com.ezansi.app.core.data.contentpack

import android.util.Log
import com.ezansi.app.core.common.DispatcherProvider
import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.ContentChunk
import com.ezansi.app.core.data.ContentPackRepository
import com.ezansi.app.core.data.PackMetadata
import kotlinx.coroutines.withContext

/**
 * Production implementation of [ContentPackRepository].
 *
 * Coordinates [PackManager] for lifecycle operations (install, uninstall,
 * list) and [PackVerifier] for integrity checks. All file and database
 * operations run on [DispatcherProvider.io] to keep the main thread free.
 *
 * ## Content Retrieval (Temporary)
 *
 * The [queryChunks] method currently uses simple text search (LIKE queries)
 * as a placeholder. The ai-pipeline-engineer will replace this with
 * embedding-based semantic retrieval using the pre-computed vectors and
 * FAISS index stored in each pack.
 *
 * ## Zero-Pack State (CP-09)
 *
 * When no packs are installed, [getInstalledPacks] returns an empty list
 * (not an error). The UI layer handles this gracefully with an onboarding
 * prompt to install the first content pack.
 *
 * @param packManager Manages pack lifecycle and database handles.
 * @param packVerifier Validates pack integrity via SHA-256 checksums.
 * @param dispatcherProvider Provides coroutine dispatchers for threading.
 */
class ContentPackRepositoryImpl(
    private val packManager: PackManager,
    private val packVerifier: PackVerifier,
    private val dispatcherProvider: DispatcherProvider,
) : ContentPackRepository {

    companion object {
        private const val TAG = "ContentPackRepo"
        private const val DEFAULT_TOP_K = 5
    }

    /**
     * Returns metadata for all installed and verified content packs.
     *
     * Scans the packs directory and reads manifest metadata from each
     * valid pack file. Unreadable or corrupt packs are skipped.
     */
    override suspend fun getInstalledPacks(): EzansiResult<List<PackMetadata>> {
        return withContext(dispatcherProvider.io) {
            try {
                packManager.getInstalledPacks()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list installed packs", e)
                EzansiResult.Error(
                    "Something went wrong loading your content packs",
                    cause = e,
                )
            }
        }
    }

    /**
     * Installs and verifies a content pack from the given file path.
     *
     * The pack is verified for SHA-256 integrity, copied atomically to
     * the packs directory, and re-verified after copy. If any step fails,
     * no partial state remains.
     *
     * @param path Absolute path to the .pack file on local storage.
     */
    override suspend fun loadPack(path: String): EzansiResult<PackMetadata> {
        return withContext(dispatcherProvider.io) {
            try {
                packManager.installPack(path)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pack from: $path", e)
                EzansiResult.Error(
                    "Something went wrong installing your content pack",
                    cause = e,
                )
            }
        }
    }

    /**
     * Verifies the integrity of a content pack without installing it.
     *
     * Runs the full SHA-256 checksum verification pipeline. Useful for
     * pre-validating a pack before committing to installation.
     *
     * @param path Absolute path to the .pack file.
     */
    override suspend fun verifyPack(path: String): EzansiResult<Boolean> {
        return withContext(dispatcherProvider.io) {
            try {
                val packFile = java.io.File(path)
                packVerifier.verify(packFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify pack: $path", e)
                EzansiResult.Error(
                    "Something went wrong verifying the content pack",
                    cause = e,
                )
            }
        }
    }

    /**
     * Returns metadata for a specific installed pack.
     *
     * Opens the pack database briefly to read manifest metadata,
     * then releases the handle.
     *
     * @param packId Unique pack identifier (e.g. "maths-grade6-caps").
     */
    override suspend fun getPackMetadata(packId: String): EzansiResult<PackMetadata> {
        return withContext(dispatcherProvider.io) {
            try {
                val database = packManager.openPack(packId)
                    ?: return@withContext EzansiResult.Error(
                        "Content pack not found: $packId",
                    )
                EzansiResult.Success(database.getMetadata())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get metadata for pack: $packId", e)
                EzansiResult.Error(
                    "Something went wrong reading the content pack",
                    cause = e,
                )
            }
        }
    }

    /**
     * Queries content chunks using text-based search.
     *
     * **Current implementation:** Simple LIKE-based text search against
     * chunk titles and content. Returns matching chunks with a default
     * relevance score of 0.0.
     *
     * **Future upgrade (ai-pipeline-engineer):** Will be replaced with
     * embedding-based semantic retrieval using cosine similarity against
     * the pack's pre-computed embeddings and FAISS index. The relevance
     * score will then reflect actual cosine similarity (0.0–1.0).
     *
     * **Compatibility gate (EMBEDDING_CONTRACT.md §6.3):** Before querying,
     * the pack's manifest is checked for embedding compatibility. Packs
     * with incompatible schema versions, embedding dimensions, or models
     * are rejected with a clear error message — no retrieval is attempted.
     *
     * @param packId Pack to search within.
     * @param query The learner's question in natural language.
     * @param topK Maximum number of chunks to return.
     */
    override suspend fun queryChunks(
        packId: String,
        query: String,
        topK: Int,
    ): EzansiResult<List<ContentChunk>> {
        return withContext(dispatcherProvider.io) {
            try {
                // Gate retrieval on embedding compatibility (EMBEDDING_CONTRACT §6.3)
                val compatibility = packManager.checkPackCompatibility(packId)
                if (compatibility !is PackCompatibility.Compatible) {
                    val message = when (compatibility) {
                        is PackCompatibility.IncompatibleSchema -> compatibility.message
                        is PackCompatibility.IncompatibleDimension -> compatibility.message
                        is PackCompatibility.IncompatibleModel -> compatibility.message
                        is PackCompatibility.IncompatibleVersion -> compatibility.message
                        else -> "Content pack is incompatible"
                    }
                    Log.w(TAG, "Rejecting retrieval for pack '$packId': $message")
                    return@withContext EzansiResult.Error(
                        "This content pack needs to be updated for the new AI engine. " +
                            "Please download the updated version.",
                    )
                }

                val database = packManager.openPack(packId)
                    ?: return@withContext EzansiResult.Error(
                        "Content pack not found: $packId",
                    )

                val effectiveTopK = if (topK > 0) topK else DEFAULT_TOP_K
                val chunks = database.searchChunksByText(query, effectiveTopK)
                EzansiResult.Success(chunks)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query chunks in pack: $packId", e)
                EzansiResult.Error(
                    "Something went wrong searching the content pack",
                    cause = e,
                )
            }
        }
    }

    /**
     * Returns the hierarchical topic tree for a specific content pack.
     *
     * Opens the pack database and delegates to [PackDatabase.getTopics]
     * which builds the tree from chunk topic_path values. Runs on
     * [DispatcherProvider.io] to keep the main thread free.
     *
     * @param packId Pack to retrieve topics from.
     */
    override suspend fun getTopicsForPack(
        packId: String,
    ): EzansiResult<List<TopicNode>> {
        return withContext(dispatcherProvider.io) {
            try {
                val database = packManager.openPack(packId)
                    ?: return@withContext EzansiResult.Error(
                        "Content pack not found: $packId",
                    )
                EzansiResult.Success(database.getTopics())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get topics for pack: $packId", e)
                EzansiResult.Error(
                    "Something went wrong loading topics",
                    cause = e,
                )
            }
        }
    }
}
