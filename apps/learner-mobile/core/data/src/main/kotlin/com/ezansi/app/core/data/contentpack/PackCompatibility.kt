package com.ezansi.app.core.data.contentpack

/**
 * Result of checking a content pack's embedding compatibility.
 *
 * When a content pack is opened for retrieval, the loader reads manifest
 * fields (`schema_version`, `embedding_model`, `embedding_dim`,
 * `embedding_model_version`) and returns one of these cases. Only
 * [Compatible] packs are fed into the retrieval pipeline — all other
 * cases surface a message to the learner explaining why an update is needed.
 *
 * See [EMBEDDING_CONTRACT.md §6](../../../../tools/content-pack-builder/EMBEDDING_CONTRACT.md)
 * for the full compatibility matrix and detection rules.
 */
sealed class PackCompatibility {

    /** Pack is fully compatible — safe to load into the retrieval pipeline. */
    data object Compatible : PackCompatibility()

    /**
     * Pack uses an older schema version (e.g. v1 with 384-dim MiniLM embeddings).
     * The pack must be rebuilt with the current builder before retrieval works.
     */
    data class IncompatibleSchema(val message: String) : PackCompatibility()

    /**
     * Pack embedding dimension does not match the on-device model.
     * E.g. pack has 384-dim vectors but the app expects 768-dim.
     */
    data class IncompatibleDimension(val message: String) : PackCompatibility()

    /**
     * Pack was built with a different embedding model.
     * E.g. pack uses MiniLM but the app expects Gemma 4.
     */
    data class IncompatibleModel(val message: String) : PackCompatibility()

    /**
     * Pack embedding model version differs from the app's active version.
     * E.g. pack uses hash-based fallback but the app now has real inference,
     * or vice versa.
     */
    data class IncompatibleVersion(val message: String) : PackCompatibility()
}
