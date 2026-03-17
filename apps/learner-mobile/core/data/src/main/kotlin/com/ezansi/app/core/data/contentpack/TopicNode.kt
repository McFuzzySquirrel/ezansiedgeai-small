package com.ezansi.app.core.data.contentpack

/**
 * A node in the CAPS curriculum topic tree, parsed from chunk topic_path values.
 *
 * Topic paths follow a dot-separated hierarchy (e.g. "term1.fractions.basics")
 * which this tree structure materialises into navigable nodes for the
 * topic browser UI.
 *
 * Example tree for a fractions pack:
 * ```
 * term1 (10 chunks)
 * └── fractions (10 chunks)
 *     ├── basics (2 chunks)
 *     ├── equivalent (1 chunk)
 *     ├── simplifying (1 chunk)
 *     └── comparing (1 chunk)
 * ```
 */
data class TopicNode(
    /** Full dot-separated path (e.g. "term1.fractions.basics"). */
    val path: String,
    /** Display name — the last segment of the path (e.g. "basics"). */
    val name: String,
    /** Child topics nested under this node. */
    val children: List<TopicNode>,
    /** Total number of content chunks at or below this node. */
    val chunkCount: Int,
)
