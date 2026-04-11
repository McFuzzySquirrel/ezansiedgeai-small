package com.ezansi.app.core.ai.retrieval

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the cosine similarity computation used in content retrieval.
 *
 * Tests the mathematical foundation of the retrieval pipeline: dot product
 * of L2-normalised vectors equals cosine similarity. This validates the
 * algorithm used by [CosineSimilarityRetriever] without requiring Android
 * SQLite or PackManager dependencies.
 *
 * Key properties tested:
 * - Identical L2-normalised vectors → score 1.0
 * - Orthogonal vectors → score 0.0
 * - Opposite vectors → score -1.0
 * - Top-K selection sorts by descending score
 * - Dimension mismatch is detected
 */
@DisplayName("Cosine Similarity Retrieval")
class CosineSimilarityRetrieverTest {

    /**
     * Computes dot product of two vectors — mirrors the private
     * computeDotProduct method in CosineSimilarityRetriever.
     */
    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Dimension mismatch: ${a.size} != ${b.size}"
        }
        var sum = 0.0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /** L2-normalises a vector in-place and returns it. */
    private fun l2Normalise(v: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (value in v) sumSq += value * value
        val mag = sqrt(sumSq.toDouble()).toFloat()
        if (mag > 0f) {
            for (i in v.indices) v[i] /= mag
        }
        return v
    }

    // ── Cosine similarity (dot product of normalised vectors) ───────

    @Nested
    @DisplayName("Cosine similarity computation")
    inner class CosineSimilarityTests {

        @Test
        @DisplayName("identical normalised vectors → score 1.0")
        fun identicalVectorsScore1() {
            val v = l2Normalise(floatArrayOf(1f, 2f, 3f))
            val score = dotProduct(v, v.copyOf())

            assertTrue(
                abs(score - 1.0f) < 0.0001f,
                "Identical vectors should have score ≈ 1.0, got $score",
            )
        }

        @Test
        @DisplayName("orthogonal vectors → score 0.0")
        fun orthogonalVectorsScore0() {
            val a = l2Normalise(floatArrayOf(1f, 0f, 0f))
            val b = l2Normalise(floatArrayOf(0f, 1f, 0f))
            val score = dotProduct(a, b)

            assertTrue(
                abs(score) < 0.0001f,
                "Orthogonal vectors should have score ≈ 0.0, got $score",
            )
        }

        @Test
        @DisplayName("opposite vectors → score -1.0")
        fun oppositeVectorsScoreNeg1() {
            val a = l2Normalise(floatArrayOf(1f, 0f, 0f))
            val b = l2Normalise(floatArrayOf(-1f, 0f, 0f))
            val score = dotProduct(a, b)

            assertTrue(
                abs(score + 1.0f) < 0.0001f,
                "Opposite vectors should have score ≈ -1.0, got $score",
            )
        }

        @Test
        @DisplayName("similar vectors → high positive score")
        fun similarVectorsHighScore() {
            val a = l2Normalise(floatArrayOf(1f, 2f, 3f))
            val b = l2Normalise(floatArrayOf(1.1f, 2.1f, 3.1f))
            val score = dotProduct(a, b)

            assertTrue(score > 0.99f, "Similar vectors should have high score, got $score")
        }

        @Test
        @DisplayName("dissimilar vectors → low score")
        fun dissimilarVectorsLowScore() {
            val a = l2Normalise(floatArrayOf(1f, 0f, 0f))
            val b = l2Normalise(floatArrayOf(0f, 0f, 1f))
            val score = dotProduct(a, b)

            assertTrue(
                abs(score) < 0.01f,
                "Dissimilar (orthogonal) vectors should have low score, got $score",
            )
        }

        @Test
        @DisplayName("works with 384-dimensional vectors")
        fun works384Dimensions() {
            val dim = 384
            val a = FloatArray(dim) { (it + 1).toFloat() }
            val b = FloatArray(dim) { (it + 1).toFloat() }
            l2Normalise(a)
            l2Normalise(b)

            val score = dotProduct(a, b)
            assertTrue(
                abs(score - 1.0f) < 0.0001f,
                "384-dim identical vectors should have score ≈ 1.0, got $score",
            )
        }
    }

    // ── Top-K ranking ───────────────────────────────────────────────

    /** Simple scored item for top-K ranking tests. */
    private class ScoredItem(val id: String, val score: Float)

    @Nested
    @DisplayName("Top-K ranking logic")
    inner class TopKRankingTests {

        /** Mimics the ranking logic in CosineSimilarityRetriever.retrieve. */
        private fun rankTopK(
            items: List<ScoredItem>,
            topK: Int,
            minThreshold: Float = 0.1f,
        ): List<ScoredItem> {
            return items
                .filter { it.score >= minThreshold }
                .sortedByDescending { it.score }
                .take(topK)
        }

        @Test
        @DisplayName("returns top-K results sorted by descending score")
        fun topKSortedDescending() {
            val items = listOf(
                ScoredItem("c", 0.5f),
                ScoredItem("a", 0.9f),
                ScoredItem("d", 0.3f),
                ScoredItem("b", 0.7f),
            )

            val top3 = rankTopK(items, topK = 3)
            assertEquals(3, top3.size)
            assertEquals("a", top3[0].id)
            assertEquals("b", top3[1].id)
            assertEquals("c", top3[2].id)
        }

        @Test
        @DisplayName("filters out items below minimum threshold")
        fun filtersLowScoreItems() {
            val items = listOf(
                ScoredItem("high", 0.8f),
                ScoredItem("low", 0.05f),
                ScoredItem("medium", 0.5f),
            )

            val results = rankTopK(items, topK = 5, minThreshold = 0.1f)
            assertEquals(2, results.size)
            assertTrue(results.none { it.id == "low" })
        }

        @Test
        @DisplayName("returns fewer than topK when not enough results")
        fun fewerThanTopK() {
            val items = listOf(
                ScoredItem("only", 0.8f),
            )

            val results = rankTopK(items, topK = 3)
            assertEquals(1, results.size)
        }

        @Test
        @DisplayName("returns empty list for empty input")
        fun emptyInput() {
            val results = rankTopK(emptyList(), topK = 3)
            assertTrue(results.isEmpty())
        }

        @Test
        @DisplayName("returns empty when all items below threshold")
        fun allBelowThreshold() {
            val items = listOf(
                ScoredItem("a", 0.05f),
                ScoredItem("b", 0.09f),
            )

            val results = rankTopK(items, topK = 3, minThreshold = 0.1f)
            assertTrue(results.isEmpty())
        }
    }

    // ── Dimension validation ────────────────────────────────────────

    @Nested
    @DisplayName("Dimension validation")
    inner class DimensionValidationTests {

        @Test
        @DisplayName("dimension mismatch throws IllegalArgumentException")
        fun dimensionMismatchThrows() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1f, 2f)

            try {
                dotProduct(a, b)
                assertTrue(false, "Should have thrown")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("mismatch"))
            }
        }

        @Test
        @DisplayName("zero-length vectors produce zero dot product")
        fun zeroLengthVectors() {
            val score = dotProduct(floatArrayOf(), floatArrayOf())
            assertEquals(0.0f, score)
        }
    }
}
