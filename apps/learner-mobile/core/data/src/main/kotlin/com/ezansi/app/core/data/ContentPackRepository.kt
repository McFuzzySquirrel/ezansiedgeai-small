package com.ezansi.app.core.data

import com.ezansi.app.core.common.EzansiResult
import com.ezansi.app.core.data.contentpack.TopicNode

/**
 * Repository for managing installed content packs.
 *
 * Content packs are self-contained SQLite bundles (.pack files) that hold
 * curriculum-aligned content chunks and pre-computed embeddings for a single
 * subject/grade combination (e.g. "Grade 6 Maths CAPS").
 *
 * Implementors (content-pack-engineer) must ensure:
 * - SHA-256 verification before any pack data enters the runtime
 * - No partial loads — a pack is fully verified or rejected
 * - All operations work offline with no network fallback
 *
 * @see PackMetadata for the metadata structure
 * @see ContentChunk for individual content items
 */
interface ContentPackRepository {

    /**
     * Returns metadata for all installed and verified content packs.
     */
    suspend fun getInstalledPacks(): EzansiResult<List<PackMetadata>>

    /**
     * Installs and verifies a content pack from the given file path.
     *
     * @param path Absolute path to the .pack file on local storage.
     * @return [EzansiResult.Success] with metadata if verification passes,
     *         [EzansiResult.Error] if SHA-256 check fails or pack is malformed.
     */
    suspend fun loadPack(path: String): EzansiResult<PackMetadata>

    /**
     * Verifies the integrity of a content pack without installing it.
     *
     * @param path Absolute path to the .pack file.
     * @return `true` if all SHA-256 checksums match the manifest.
     */
    suspend fun verifyPack(path: String): EzansiResult<Boolean>

    /**
     * Returns metadata for a specific installed pack.
     *
     * @param packId Unique pack identifier (e.g. "maths-grade6-caps").
     */
    suspend fun getPackMetadata(packId: String): EzansiResult<PackMetadata>

    /**
     * Queries content chunks using semantic similarity search.
     *
     * Retrieves the top-K most relevant chunks from a pack's pre-computed
     * embedding index, ranked by cosine similarity to the query embedding.
     *
     * @param packId Pack to search within.
     * @param query The learner's question in natural language.
     * @param topK Maximum number of chunks to return (default: 5).
     * @return Ranked list of content chunks with relevance scores.
     */
    suspend fun queryChunks(
        packId: String,
        query: String,
        topK: Int = 5,
    ): EzansiResult<List<ContentChunk>>

    /**
     * Returns the hierarchical topic tree for a specific content pack.
     *
     * The tree is built from the dot-separated `topic_path` values stored
     * in the pack's chunks table. Each node contains the display name,
     * full path, child nodes, and the total chunk count at or below it.
     *
     * Used by the topic browser UI to present the CAPS curriculum
     * navigation hierarchy (e.g. Term → Strand → Topic).
     *
     * @param packId Pack to retrieve topics from.
     * @return Hierarchical list of root-level [TopicNode] entries.
     */
    suspend fun getTopicsForPack(
        packId: String,
    ): EzansiResult<List<TopicNode>>
}

/**
 * Metadata describing an installed content pack.
 */
data class PackMetadata(
    /** Unique identifier (e.g. "maths-grade6-caps"). */
    val packId: String,
    /** Human-readable display name. */
    val displayName: String,
    /** Semantic version (e.g. "1.0.0"). */
    val version: String,
    /** Subject area (e.g. "Mathematics"). */
    val subject: String,
    /** Grade level (e.g. "6"). */
    val grade: String,
    /** Curriculum standard (e.g. "CAPS"). */
    val curriculum: String,
    /** Total size in bytes on disk. */
    val sizeBytes: Long,
    /** Number of content chunks in this pack. */
    val chunkCount: Int,
    /** Locale/language code (e.g. "en-ZA"). */
    val locale: String,
)

/**
 * A single content chunk retrieved from a pack.
 */
data class ContentChunk(
    /** Unique chunk identifier within the pack. */
    val chunkId: String,
    /** Pack this chunk belongs to. */
    val packId: String,
    /** Display title for this chunk. */
    val title: String,
    /** CAPS topic path (e.g. "term1.week3.fractions.addition"). */
    val topicPath: String,
    /** The actual content in Markdown format. */
    val content: String,
    /** Relevance score from similarity search (0.0–1.0). */
    val relevanceScore: Float = 0f,
    /** Difficulty level (e.g. "basic", "intermediate", "advanced"). */
    val difficulty: String = "",
    /** CAPS term number (1–4). */
    val term: Int = 0,
)
