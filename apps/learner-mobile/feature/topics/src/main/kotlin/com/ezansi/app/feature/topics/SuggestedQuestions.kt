package com.ezansi.app.feature.topics

/**
 * Generates contextual suggested questions for a given CAPS topic.
 *
 * Uses topic path keywords and chunk titles to create grade-appropriate
 * questions that learners can tap to pre-fill in the chat. All questions
 * are written at Grade 4 reading level with simple vocabulary.
 *
 * ## Strategy
 *
 * 1. Match topic path segments against a keyword → template map
 * 2. If chunk titles are available, derive questions from those
 * 3. Fall back to generic templates using the topic display name
 * 4. Return 3–5 questions, never more, never fewer than 1
 *
 * ## Examples
 *
 * For topic path "term1.fractions.comparing":
 * - "What are fractions?"
 * - "How do I compare fractions?"
 * - "Show me an example of comparing fractions"
 *
 * @see TopicsViewModel for usage context
 */
object SuggestedQuestions {

    /**
     * Generates suggested questions for a topic.
     *
     * @param topicPath Dot-separated CAPS topic path (e.g. "term1.fractions.basics").
     * @param topicName Display name of the topic (e.g. "basics").
     * @param chunkTitles Optional list of chunk titles under this topic.
     * @return 3–5 grade-appropriate questions the learner can ask.
     */
    fun generate(
        topicPath: String,
        topicName: String,
        chunkTitles: List<String> = emptyList(),
    ): List<String> {
        val questions = mutableListOf<String>()

        // 1. Add keyword-matched questions from the topic path
        val pathSegments = topicPath.lowercase().split(".")
        for (segment in pathSegments) {
            KEYWORD_TEMPLATES[segment]?.let { templates ->
                questions.addAll(templates)
            }
        }

        // 2. Add questions derived from chunk titles
        for (title in chunkTitles.take(MAX_CHUNK_QUESTIONS)) {
            val cleanTitle = title.trim()
            if (cleanTitle.isNotBlank()) {
                questions.add("Explain $cleanTitle")
            }
        }

        // 3. Add generic topic-name-based questions as fallback
        val displayName = formatTopicName(topicName)
        if (questions.isEmpty()) {
            questions.addAll(genericQuestions(displayName))
        }

        // 4. Ensure we always include a "What is…" opener if not present
        val hasOpener = questions.any { it.startsWith("What", ignoreCase = true) }
        if (!hasOpener) {
            questions.add(0, "What is $displayName?")
        }

        // Return 3–5 unique questions
        return questions
            .distinct()
            .take(MAX_QUESTIONS)
            .ifEmpty { genericQuestions(displayName) }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private const val MAX_QUESTIONS = 5
    private const val MAX_CHUNK_QUESTIONS = 3

    /**
     * Converts a path segment into a readable display name.
     * "whole_numbers" → "whole numbers", "fractions" → "fractions"
     */
    private fun formatTopicName(name: String): String {
        return name
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    /** Fallback questions using just the topic display name. */
    private fun genericQuestions(displayName: String): List<String> {
        return listOf(
            "What is $displayName?",
            "Explain $displayName in a simple way",
            "Give me an example of $displayName",
        )
    }

    /**
     * Keyword-to-question templates for common CAPS maths topics.
     *
     * Each keyword maps to 2–3 grade-appropriate questions. Keywords
     * are matched against segments of the dot-separated topic path.
     */
    private val KEYWORD_TEMPLATES: Map<String, List<String>> = mapOf(
        // ── Numbers & Operations ────────────────────────────────
        "fractions" to listOf(
            "What are fractions?",
            "How do I compare fractions?",
            "How do I add fractions with different denominators?",
        ),
        "decimals" to listOf(
            "What are decimals?",
            "How do I convert fractions to decimals?",
            "How do I add and subtract decimals?",
        ),
        "whole_numbers" to listOf(
            "How do I work with large numbers?",
            "How do I multiply whole numbers?",
            "Explain place value for whole numbers",
        ),
        "wholenumbers" to listOf(
            "How do I work with large numbers?",
            "How do I multiply whole numbers?",
            "Explain place value for whole numbers",
        ),
        "percentages" to listOf(
            "What are percentages?",
            "How do I calculate a percentage?",
            "How do I convert fractions to percentages?",
        ),
        "numbers" to listOf(
            "What are the different types of numbers?",
            "How do number patterns work?",
        ),
        "operations" to listOf(
            "What are the four basic operations?",
            "How do I know which operation to use?",
        ),

        // ── Patterns & Algebra ──────────────────────────────────
        "patterns" to listOf(
            "What are number patterns?",
            "How do I find the next number in a pattern?",
            "How do I describe a pattern rule?",
        ),
        "algebra" to listOf(
            "What is algebra?",
            "How do I solve for an unknown number?",
        ),

        // ── Geometry ────────────────────────────────────────────
        "geometry" to listOf(
            "What are the basic shapes?",
            "How do I identify 3D objects?",
            "What is symmetry?",
        ),
        "shapes" to listOf(
            "What are 2D shapes?",
            "How do I name different shapes?",
            "What are the properties of shapes?",
        ),
        "angles" to listOf(
            "What are angles?",
            "How do I measure an angle?",
            "What are right angles, acute angles, and obtuse angles?",
        ),
        "symmetry" to listOf(
            "What is symmetry?",
            "How do I find lines of symmetry?",
        ),
        "transformations" to listOf(
            "What are transformations?",
            "How do I reflect a shape?",
            "How do I translate a shape?",
        ),

        // ── Measurement ─────────────────────────────────────────
        "measurement" to listOf(
            "What units do I use to measure things?",
            "How do I convert between units?",
            "How do I measure length, mass, and volume?",
        ),
        "length" to listOf(
            "How do I measure length?",
            "How do I convert millimetres to centimetres?",
        ),
        "mass" to listOf(
            "How do I measure mass?",
            "What is the difference between mass and weight?",
        ),
        "volume" to listOf(
            "How do I measure volume?",
            "What is the difference between volume and capacity?",
        ),
        "area" to listOf(
            "What is area?",
            "How do I calculate the area of a rectangle?",
            "How do I calculate the area of a triangle?",
        ),
        "perimeter" to listOf(
            "What is perimeter?",
            "How do I calculate the perimeter of a shape?",
        ),
        "time" to listOf(
            "How do I read a clock?",
            "How do I calculate time differences?",
        ),

        // ── Data Handling ───────────────────────────────────────
        "data" to listOf(
            "What is data handling?",
            "How do I read a bar graph?",
            "How do I collect and organise data?",
        ),
        "graphs" to listOf(
            "How do I draw a bar graph?",
            "How do I read a pie chart?",
        ),
        "probability" to listOf(
            "What is probability?",
            "How do I calculate the chance of something happening?",
        ),

        // ── Topic path structural segments ──────────────────────
        "basics" to listOf(
            "Explain the basics step by step",
        ),
        "comparing" to listOf(
            "How do I compare these numbers?",
        ),
        "equivalent" to listOf(
            "What does equivalent mean?",
            "How do I find equivalent values?",
        ),
        "simplifying" to listOf(
            "How do I simplify step by step?",
        ),
        "addition" to listOf(
            "How do I add these?",
            "Show me an example of addition",
        ),
        "subtraction" to listOf(
            "How do I subtract these?",
            "Show me an example of subtraction",
        ),
        "multiplication" to listOf(
            "How do I multiply?",
            "Show me an example of multiplication",
        ),
        "division" to listOf(
            "How do I divide?",
            "Show me an example of division",
        ),
    )
}
