package com.ezansi.app.core.data.contentpack

/**
 * Detects content pack compatibility by reading manifest fields.
 *
 * Called at pack-open time (before any retrieval queries) to ensure the
 * pack's pre-computed embeddings are compatible with the on-device
 * embedding model. Incompatible packs are rejected with a descriptive
 * message that the UI surfaces to the learner.
 *
 * ## Detection Rules (EMBEDDING_CONTRACT.md §6.2)
 *
 * 1. `schema_version` must be ≥ [REQUIRED_SCHEMA_VERSION] (2)
 * 2. `embedding_dim` must be [REQUIRED_EMBEDDING_DIM] (768)
 * 3. `embedding_model` must be [REQUIRED_EMBEDDING_MODEL] ("gemma4-1b")
 * 4. `embedding_model_version` — if present — must match
 *    [CURRENT_EMBEDDING_VERSION]; if absent, the pack is still considered
 *    compatible (the field was added after schema v2 was introduced)
 *
 * ## Default Assumptions
 *
 * Missing manifest fields are interpreted as v1 (MiniLM) pack values:
 * - `schema_version` defaults to 1
 * - `embedding_dim` defaults to 384
 * - `embedding_model` defaults to `sentence-transformers/all-MiniLM-L6-v2`
 * - `embedding_model_version` defaults to null (not checked)
 *
 * @see PackCompatibility for possible outcomes
 */
object PackVersionDetector {

    /** Minimum schema version required for retrieval compatibility. */
    const val REQUIRED_SCHEMA_VERSION = 2

    /** Embedding dimension the on-device model produces. */
    const val REQUIRED_EMBEDDING_DIM = 768

    /** Logical model identifier the app expects in pack manifests. */
    const val REQUIRED_EMBEDDING_MODEL = "gemma4-1b"

    /** Current embedding implementation version (hash-based fallback). */
    const val CURRENT_EMBEDDING_VERSION = "gemma4-1b-hash-v1"

    /**
     * Checks whether a content pack's manifest indicates compatibility
     * with the current on-device embedding model.
     *
     * @param manifest Key-value pairs read from the pack's `manifest` table.
     * @return [PackCompatibility.Compatible] if the pack can be used for
     *         retrieval, or one of the incompatible subtypes with a
     *         human-readable message explaining the mismatch.
     */
    fun checkCompatibility(manifest: Map<String, String>): PackCompatibility {
        val schemaVersion = manifest["schema_version"]?.toIntOrNull() ?: 1
        val embeddingDim = manifest["embedding_dim"]?.toIntOrNull() ?: 384
        val embeddingModel = manifest["embedding_model"]
            ?: "sentence-transformers/all-MiniLM-L6-v2"
        val embeddingVersion = manifest["embedding_model_version"]

        return when {
            schemaVersion < REQUIRED_SCHEMA_VERSION ->
                PackCompatibility.IncompatibleSchema(
                    "Pack uses schema v$schemaVersion; v$REQUIRED_SCHEMA_VERSION required. " +
                        "Please download the updated content pack.",
                )

            embeddingDim != REQUIRED_EMBEDDING_DIM ->
                PackCompatibility.IncompatibleDimension(
                    "Pack uses ${embeddingDim}-dim embeddings; " +
                        "${REQUIRED_EMBEDDING_DIM}-dim required. " +
                        "Please download the updated content pack.",
                )

            embeddingModel != REQUIRED_EMBEDDING_MODEL ->
                PackCompatibility.IncompatibleModel(
                    "Pack uses $embeddingModel; $REQUIRED_EMBEDDING_MODEL required. " +
                        "Please download the updated content pack.",
                )

            embeddingVersion != null && embeddingVersion != CURRENT_EMBEDDING_VERSION ->
                PackCompatibility.IncompatibleVersion(
                    "Pack embedding version ($embeddingVersion) does not match " +
                        "the app's active version ($CURRENT_EMBEDDING_VERSION). " +
                        "Please download the updated content pack.",
                )

            else -> PackCompatibility.Compatible
        }
    }
}
