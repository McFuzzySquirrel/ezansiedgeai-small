package com.ezansi.app.core.data.contentpack

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [TopicNode] data class and topic tree construction.
 *
 * The topic tree is built from flat dot-separated paths (e.g. "term1.fractions.basics")
 * into a navigable hierarchy used by the topic browser UI. This test verifies
 * both the data class itself and the tree-building algorithm logic.
 */
@DisplayName("TopicNode")
class TopicNodeTest {

    // ── Data class basics ───────────────────────────────────────────

    @Nested
    @DisplayName("Data class properties")
    inner class DataClassTests {

        @Test
        @DisplayName("stores path, name, children, and chunkCount")
        fun storesAllFields() {
            val node = TopicNode(
                path = "term1.fractions",
                name = "fractions",
                children = emptyList(),
                chunkCount = 5,
            )

            assertEquals("term1.fractions", node.path)
            assertEquals("fractions", node.name)
            assertTrue(node.children.isEmpty())
            assertEquals(5, node.chunkCount)
        }

        @Test
        @DisplayName("supports nested children")
        fun supportsNestedChildren() {
            val leaf = TopicNode("term1.fractions.basics", "basics", emptyList(), 2)
            val parent = TopicNode("term1.fractions", "fractions", listOf(leaf), 5)

            assertEquals(1, parent.children.size)
            assertEquals("basics", parent.children[0].name)
            assertEquals(2, parent.children[0].chunkCount)
        }

        @Test
        @DisplayName("equality based on all fields")
        fun equalityContract() {
            val a = TopicNode("path", "name", emptyList(), 3)
            val b = TopicNode("path", "name", emptyList(), 3)
            assertEquals(a, b)
        }
    }

    // ── Tree building from flat paths ───────────────────────────────

    @Nested
    @DisplayName("Tree building from flat paths")
    inner class TreeBuildingTests {

        /**
         * Builds a topic tree from flat topic path counts.
         *
         * This mirrors the algorithm used in PackDatabase.buildTopicTree
         * so we can test the tree-building logic without Android SQLite.
         */
        private fun buildTopicTree(topicCounts: Map<String, Int>): List<TopicNode> {
            val allPrefixes = mutableSetOf<String>()
            for (path in topicCounts.keys) {
                val segments = path.split(".")
                for (i in segments.indices) {
                    allPrefixes.add(segments.subList(0, i + 1).joinToString("."))
                }
            }
            return buildChildNodes("", allPrefixes, topicCounts)
        }

        private fun buildChildNodes(
            parentPrefix: String,
            allPrefixes: Set<String>,
            topicCounts: Map<String, Int>,
        ): List<TopicNode> {
            val parentDepth = if (parentPrefix.isEmpty()) 0 else parentPrefix.count { it == '.' } + 1
            val directChildren = allPrefixes.filter { prefix ->
                val prefixDepth = prefix.count { it == '.' } + 1
                prefixDepth == parentDepth + 1 &&
                    (parentPrefix.isEmpty() || prefix.startsWith("$parentPrefix."))
            }.sorted()

            return directChildren.map { childPrefix ->
                val chunkCount = topicCounts.entries
                    .filter { (path, _) ->
                        path == childPrefix || path.startsWith("$childPrefix.")
                    }
                    .sumOf { it.value }

                TopicNode(
                    path = childPrefix,
                    name = childPrefix.substringAfterLast("."),
                    children = buildChildNodes(childPrefix, allPrefixes, topicCounts),
                    chunkCount = chunkCount,
                )
            }
        }

        @Test
        @DisplayName("single flat path creates single root node")
        fun singleFlatPath() {
            val tree = buildTopicTree(mapOf("fractions" to 3))

            assertEquals(1, tree.size)
            assertEquals("fractions", tree[0].name)
            assertEquals("fractions", tree[0].path)
            assertEquals(3, tree[0].chunkCount)
            assertTrue(tree[0].children.isEmpty())
        }

        @Test
        @DisplayName("two-level path creates parent with child")
        fun twoLevelPath() {
            val tree = buildTopicTree(mapOf("term1.fractions" to 5))

            assertEquals(1, tree.size)
            assertEquals("term1", tree[0].name)
            assertEquals(5, tree[0].chunkCount)
            assertEquals(1, tree[0].children.size)
            assertEquals("fractions", tree[0].children[0].name)
            assertEquals(5, tree[0].children[0].chunkCount)
        }

        @Test
        @DisplayName("three-level path creates full hierarchy")
        fun threeLevelPath() {
            val tree = buildTopicTree(
                mapOf("term1.fractions.basics" to 2),
            )

            assertEquals(1, tree.size)
            val term = tree[0]
            assertEquals("term1", term.name)
            assertEquals(2, term.chunkCount)

            val fractions = term.children[0]
            assertEquals("fractions", fractions.name)
            assertEquals(2, fractions.chunkCount)

            val basics = fractions.children[0]
            assertEquals("basics", basics.name)
            assertEquals(2, basics.chunkCount)
            assertTrue(basics.children.isEmpty())
        }

        @Test
        @DisplayName("multiple paths merge into shared tree nodes")
        fun multiplePathsMerge() {
            val tree = buildTopicTree(
                mapOf(
                    "term1.fractions.basics" to 2,
                    "term1.fractions.comparing" to 3,
                    "term1.decimals.basics" to 1,
                ),
            )

            assertEquals(1, tree.size)
            val term = tree[0]
            assertEquals("term1", term.name)
            assertEquals(6, term.chunkCount) // 2 + 3 + 1

            assertEquals(2, term.children.size)

            val decimals = term.children.find { it.name == "decimals" }!!
            assertEquals(1, decimals.chunkCount)

            val fractions = term.children.find { it.name == "fractions" }!!
            assertEquals(5, fractions.chunkCount) // 2 + 3
            assertEquals(2, fractions.children.size)
        }

        @Test
        @DisplayName("multiple root nodes for different terms")
        fun multipleRootNodes() {
            val tree = buildTopicTree(
                mapOf(
                    "term1.fractions" to 3,
                    "term2.decimals" to 4,
                ),
            )

            assertEquals(2, tree.size)
            assertTrue(tree.any { it.name == "term1" })
            assertTrue(tree.any { it.name == "term2" })
        }

        @Test
        @DisplayName("empty input produces empty tree")
        fun emptyInput() {
            val tree = buildTopicTree(emptyMap())
            assertTrue(tree.isEmpty())
        }

        @Test
        @DisplayName("chunk counts propagate correctly from leaves to roots")
        fun chunkCountsPropagateCorrectly() {
            val tree = buildTopicTree(
                mapOf(
                    "term1.fractions.basics" to 2,
                    "term1.fractions.advanced" to 3,
                    "term1.decimals" to 4,
                ),
            )

            val term = tree[0]
            assertEquals(9, term.chunkCount) // 2 + 3 + 4

            val fractions = term.children.find { it.name == "fractions" }!!
            assertEquals(5, fractions.chunkCount) // 2 + 3

            val decimals = term.children.find { it.name == "decimals" }!!
            assertEquals(4, decimals.chunkCount)
        }

        @Test
        @DisplayName("children are sorted alphabetically by path")
        fun childrenAreSorted() {
            val tree = buildTopicTree(
                mapOf(
                    "term1.zebra" to 1,
                    "term1.apple" to 2,
                    "term1.mango" to 3,
                ),
            )

            val children = tree[0].children
            assertEquals("apple", children[0].name)
            assertEquals("mango", children[1].name)
            assertEquals("zebra", children[2].name)
        }
    }
}
