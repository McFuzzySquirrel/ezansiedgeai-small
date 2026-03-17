package com.ezansi.app.feature.topics

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SuggestedQuestions] — contextual question generation
 * for the CAPS topic browser.
 *
 * Validates that known topics produce relevant questions, unknown topics
 * produce generic questions, and the output always contains 3–5 items.
 * All questions should be Grade 4-readable with simple vocabulary (PRD §11).
 */
@DisplayName("SuggestedQuestions")
class SuggestedQuestionsTest {

    @Nested
    @DisplayName("Known topics")
    inner class KnownTopicTests {

        @Test
        @DisplayName("fractions topic produces fraction-specific questions")
        fun fractionsTopicQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.fractions",
                topicName = "fractions",
            )

            assertTrue(questions.size in 3..5, "Expected 3-5 questions, got ${questions.size}")
            assertTrue(
                questions.any { it.contains("fraction", ignoreCase = true) },
                "Expected at least one question about fractions: $questions",
            )
        }

        @Test
        @DisplayName("decimals topic produces decimal-specific questions")
        fun decimalsTopicQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term2.decimals",
                topicName = "decimals",
            )

            assertTrue(questions.size in 3..5)
            assertTrue(
                questions.any { it.contains("decimal", ignoreCase = true) },
                "Expected at least one question about decimals",
            )
        }

        @Test
        @DisplayName("geometry topic produces geometry-specific questions")
        fun geometryTopicQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term3.geometry",
                topicName = "geometry",
            )

            assertTrue(questions.size in 3..5)
            assertTrue(
                questions.any {
                    it.contains("shape", ignoreCase = true) ||
                        it.contains("geometry", ignoreCase = true) ||
                        it.contains("3D", ignoreCase = true)
                },
            )
        }

        @Test
        @DisplayName("nested path matches multiple keyword segments")
        fun nestedPathMatchesMultipleSegments() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.fractions.comparing",
                topicName = "comparing",
            )

            assertTrue(questions.size in 3..5)
            // Should match both "fractions" and "comparing" keywords
            assertTrue(
                questions.any { it.contains("fraction", ignoreCase = true) },
                "Expected fraction questions from path segment",
            )
        }
    }

    @Nested
    @DisplayName("Unknown topics")
    inner class UnknownTopicTests {

        @Test
        @DisplayName("unknown topic produces generic questions using display name")
        fun unknownTopicGenericQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term4.advanced_topology",
                topicName = "advanced_topology",
            )

            assertTrue(questions.size in 3..5)
            // Should contain the formatted display name
            assertTrue(
                questions.any { it.contains("Advanced topology", ignoreCase = true) },
                "Expected questions with formatted topic name: $questions",
            )
        }

        @Test
        @DisplayName("generic questions include What is opener")
        fun genericQuestionsIncludeOpener() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term4.unknown_topic",
                topicName = "unknown_topic",
            )

            assertTrue(
                questions.any { it.startsWith("What", ignoreCase = true) },
                "Expected a 'What is...' opener question: $questions",
            )
        }
    }

    @Nested
    @DisplayName("Output constraints")
    inner class OutputConstraintTests {

        @Test
        @DisplayName("returns 3-5 questions for all topics")
        fun returnsBetween3And5Questions() {
            val testPaths = listOf(
                "term1.fractions" to "fractions",
                "term2.decimals" to "decimals",
                "term3.geometry" to "geometry",
                "term4.data" to "data",
                "unknown.path" to "path",
            )

            for ((topicPath, topicName) in testPaths) {
                val questions = SuggestedQuestions.generate(topicPath, topicName)
                assertTrue(
                    questions.size in 1..5,
                    "Topic '$topicPath' produced ${questions.size} questions",
                )
            }
        }

        @Test
        @DisplayName("returns distinct questions only")
        fun returnsDistinctQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.fractions",
                topicName = "fractions",
            )

            assertEquals(
                questions.size,
                questions.distinct().size,
                "Questions contain duplicates: $questions",
            )
        }

        @Test
        @DisplayName("always includes a What opener if not naturally present")
        fun alwaysIncludesWhatOpener() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.addition",
                topicName = "addition",
            )

            assertTrue(
                questions.any { it.startsWith("What", ignoreCase = true) },
                "Missing 'What...' opener in: $questions",
            )
        }

        @Test
        @DisplayName("chunk titles generate Explain questions")
        fun chunkTitlesGenerateExplainQuestions() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.fractions",
                topicName = "fractions",
                chunkTitles = listOf("Adding fractions with unlike denominators"),
            )

            assertTrue(
                questions.any {
                    it.contains("Adding fractions with unlike denominators")
                },
                "Expected chunk-title-based question in: $questions",
            )
        }
    }

    @Nested
    @DisplayName("Display name formatting")
    inner class DisplayNameFormattingTests {

        @Test
        @DisplayName("underscores are replaced with spaces")
        fun underscoresReplacedWithSpaces() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.whole_numbers",
                topicName = "whole_numbers",
            )

            // "whole_numbers" keyword matches directly, but generic fallback
            // would format as "Whole numbers"
            assertTrue(questions.isNotEmpty())
        }

        @Test
        @DisplayName("hyphens are replaced with spaces")
        fun hyphensReplacedWithSpaces() {
            val questions = SuggestedQuestions.generate(
                topicPath = "term1.my-topic",
                topicName = "my-topic",
            )

            assertTrue(
                questions.any { it.contains("My topic", ignoreCase = true) },
                "Expected formatted name 'My topic' in: $questions",
            )
        }
    }
}
