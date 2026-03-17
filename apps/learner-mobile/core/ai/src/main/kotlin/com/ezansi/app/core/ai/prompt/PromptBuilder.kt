package com.ezansi.app.core.ai.prompt

import com.ezansi.app.core.ai.prompt.templates.DefaultTemplates
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.data.LearnerPreference

/**
 * Builds grounded LLM prompts using data-driven Jinja2-style templates.
 *
 * The prompt builder is the third step in the RAG pipeline:
 * question → embed → retrieve → **prompt** → generate.
 *
 * It constructs a Qwen2.5 chat-template-formatted prompt that:
 * 1. Instructs the model to act as a Grade 6 maths tutor
 * 2. Includes retrieved content chunks as the ONLY knowledge source
 * 3. Adapts language and style to the learner's preferences via templates
 * 4. Enforces grounding — the model must not invent information (AI-10)
 *
 * ## Data-Driven Templates (P1-107)
 *
 * Instead of hardcoded prompt strings, this builder uses a [TemplateEngine]
 * to render Jinja2-style templates from [DefaultTemplates]. Templates support
 * variable substitution, conditionals, loops, and filters — enabling
 * preference-driven prompt adaptation without code changes.
 *
 * Future: custom templates can be loaded from content packs, version-matched
 * to the pack format, allowing content authors to tune the prompt for their
 * subject area.
 *
 * ## Token Budget (PT-04, 2048-token context window)
 *
 * | Section           | Budget      | Notes |
 * |-------------------|-------------|-------|
 * | System prompt     | ~200 tokens | Personality + preferences + grounding rules |
 * | User content      | ~1,300 tokens | Retrieved chunks + learner question |
 * | Generation budget | ~500 tokens | Reserved for LLM response |
 * | Format overhead   | ~48 tokens  | ChatML delimiters |
 *
 * If chunks exceed the user content budget, lowest-scored chunks are
 * dropped first. The last remaining chunk may be truncated if still
 * over budget.
 *
 * ## Grounding Enforcement (AI-10)
 *
 * The [GROUNDING_INSTRUCTION][DefaultTemplates.GROUNDING_INSTRUCTION] is
 * **always** appended to the system prompt. This is architecturally enforced
 * — neither custom templates nor preference injection can omit it.
 *
 * ## Template Format
 *
 * Uses Qwen2.5 ChatML-style format:
 * ```
 * <|im_start|>system
 * {system_prompt}<|im_end|>
 * <|im_start|>user
 * {user_prompt}<|im_end|>
 * <|im_start|>assistant
 * ```
 *
 * @param templateEngine The template renderer (injected for testability).
 * @see com.ezansi.app.core.ai.ExplanationEngine
 * @see DefaultTemplates for bundled template definitions
 */
class PromptBuilder(
    private val templateEngine: TemplateEngine = TemplateEngine(),
) {

    companion object {
        /** Maximum total tokens for the context window (input + output). */
        internal const val MAX_CONTEXT_TOKENS = 2048

        /** Reserved tokens for the model's generated response. */
        internal const val GENERATION_TOKEN_BUDGET = 500

        /** Tokens reserved for ChatML format overhead (delimiters, role tags). */
        internal const val FORMAT_OVERHEAD_TOKENS = 48

        /**
         * Maximum tokens available for all prompt content (system + user).
         * Calculated as: MAX_CONTEXT_TOKENS - GENERATION_TOKEN_BUDGET - FORMAT_OVERHEAD_TOKENS.
         */
        internal const val MAX_PROMPT_INPUT_TOKENS =
            MAX_CONTEXT_TOKENS - GENERATION_TOKEN_BUDGET - FORMAT_OVERHEAD_TOKENS

        // Qwen2.5 ChatML delimiters
        private const val IM_START = "<|im_start|>"
        private const val IM_END = "<|im_end|>"

        // Default preference values when learner has no preferences set
        private const val DEFAULT_EXPLANATION_STYLE = "step-by-step"
        private const val DEFAULT_READING_LEVEL = "simple"
        private const val DEFAULT_EXAMPLE_TYPE = "everyday"

        /**
         * Token estimation multiplier: words × factor ≈ token count.
         *
         * 1.3 is a reasonable approximation for English text with Qwen2.5's
         * BPE tokeniser. Slightly overestimates, giving us a safety margin.
         */
        private const val WORDS_TO_TOKENS_FACTOR = 1.3
    }

    /**
     * Builds a grounded prompt from retrieved chunks and learner preferences.
     *
     * The prompt includes the retrieved content as the sole knowledge source,
     * adapted to the learner's reading level and explanation style through
     * template rendering. The system prompt always includes the grounding
     * instruction to prevent hallucination (AI-10).
     *
     * ## Pipeline
     *
     * 1. Render system prompt from template (personality + preferences + grounding)
     * 2. Calculate remaining token budget for user content
     * 3. Fit chunks within budget, dropping lowest-scored first if needed
     * 4. Render user prompt from template (chunks + question)
     * 5. Wrap in Qwen2.5 ChatML format
     *
     * @param question The learner's question in natural language.
     * @param retrievedChunks Chunks ranked by similarity (highest first).
     * @param preferences The learner's active preferences, or null for defaults.
     * @return A complete Qwen2.5 ChatML-formatted prompt string ready for inference.
     */
    fun buildGroundedPromptFromRetrievedChunks(
        question: String,
        retrievedChunks: List<RetrievalResult>,
        preferences: List<LearnerPreference>?,
    ): String {
        // Step 1: Build the system prompt (personality + preferences + grounding)
        val systemPrompt = renderSystemPrompt(preferences)

        // Step 2: Calculate token budget remaining for the user prompt
        val systemTokens = estimateTokenCount(systemPrompt)
        val availableForUser = MAX_PROMPT_INPUT_TOKENS - systemTokens

        // Step 3: Fit chunks within the available user content budget
        val fittedChunks = fitChunksWithinBudget(
            chunks = retrievedChunks,
            question = question,
            maxUserTokens = availableForUser,
        )

        // Step 4: Render the user prompt with fitted chunks
        val userPrompt = renderUserPrompt(question, fittedChunks)

        // Step 5: Apply Qwen2.5 ChatML format
        return formatAsChatMl(systemPrompt, userPrompt)
    }

    /**
     * Estimates the token count for a given text.
     *
     * Uses a word-count heuristic: count whitespace-separated words
     * and multiply by 1.3. This approximates BPE tokenisation for English
     * text and slightly overestimates for safety.
     *
     * @param text The text to estimate token count for.
     * @return Estimated token count (minimum 1 for non-blank text).
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        val wordCount = text.split(WHITESPACE_PATTERN).count { it.isNotEmpty() }
        return maxOf(1, (wordCount * WORDS_TO_TOKENS_FACTOR).toInt())
    }

    // ── Template rendering ──────────────────────────────────────────

    /**
     * Renders the system prompt from the default template with learner preferences.
     *
     * The grounding instruction is always appended after the rendered template.
     * This architectural separation ensures that custom templates (from future
     * content pack support) cannot accidentally or deliberately omit the
     * grounding rules.
     */
    internal fun renderSystemPrompt(preferences: List<LearnerPreference>?): String {
        val context = buildPreferenceContext(preferences)

        // Render the personality + preference section
        val personalityPrompt = templateEngine.render(DefaultTemplates.SYSTEM_PROMPT, context)

        // Always append grounding instruction — this is non-negotiable (AI-10)
        val fullSystemPrompt = buildString {
            append(normalizeWhitespace(personalityPrompt))
            append("\n")
            append(DefaultTemplates.GROUNDING_INSTRUCTION)
        }

        return fullSystemPrompt.trim()
    }

    /**
     * Renders the user prompt from the default template with chunks and question.
     */
    internal fun renderUserPrompt(
        question: String,
        chunks: List<RetrievalResult>,
    ): String {
        val context = buildChunkContext(question, chunks)
        val rendered = templateEngine.render(DefaultTemplates.USER_PROMPT, context)
        return rendered.trim()
    }

    // ── Token budget management ─────────────────────────────────────

    /**
     * Fits chunks within the available token budget for the user prompt.
     *
     * Strategy:
     * 1. Calculate how many tokens the question and fixed text consume
     * 2. Remaining tokens go to chunk content
     * 3. Include chunks in similarity order (highest first)
     * 4. If a chunk doesn't fit, check if a truncated version fits
     * 5. Stop adding chunks when budget is exhausted
     *
     * Chunks are already sorted by score (highest first from the retriever),
     * so lowest-scored chunks are dropped first when budget is tight.
     *
     * @param chunks Retrieved chunks sorted by similarity (descending).
     * @param question The learner's question text.
     * @param maxUserTokens Maximum tokens for the entire user prompt.
     * @return Subset of chunks that fit within the budget (may have last chunk truncated).
     */
    internal fun fitChunksWithinBudget(
        chunks: List<RetrievalResult>,
        question: String,
        maxUserTokens: Int,
    ): List<RetrievalResult> {
        if (chunks.isEmpty()) return emptyList()

        // Estimate tokens consumed by the question and template boilerplate
        val questionOverheadTokens = estimateTokenCount(
            "CURRICULUM CONTENT (verified, CAPS-aligned — use ONLY this):\n---\n" +
                "LEARNER'S QUESTION: $question",
        )

        val availableForChunks = maxUserTokens - questionOverheadTokens
        if (availableForChunks <= 0) return emptyList()

        val fittedChunks = mutableListOf<RetrievalResult>()
        var remainingTokens = availableForChunks

        for (result in chunks) {
            val chunkText = formatChunkForEstimation(result)
            val chunkTokens = estimateTokenCount(chunkText)

            if (chunkTokens <= remainingTokens) {
                // Chunk fits entirely
                fittedChunks.add(result)
                remainingTokens -= chunkTokens
            } else if (remainingTokens > MINIMUM_USEFUL_CHUNK_TOKENS) {
                // Truncate the chunk content to fit the remaining budget
                val truncated = truncateChunkContent(result, remainingTokens)
                if (truncated != null) {
                    fittedChunks.add(truncated)
                }
                break // No room for more chunks after truncation
            } else {
                // Not enough room for even a truncated chunk — stop
                break
            }
        }

        return fittedChunks
    }

    // ── Context building ────────────────────────────────────────────

    /**
     * Builds the template context with learner preference values.
     *
     * Unknown or missing preference keys get safe defaults that produce
     * the most universally appropriate prompt variant.
     */
    private fun buildPreferenceContext(preferences: List<LearnerPreference>?): TemplateContext {
        val explanationStyle = findPreferenceValue(preferences, "explanation_style")
            ?: DEFAULT_EXPLANATION_STYLE
        val readingLevel = findPreferenceValue(preferences, "reading_level")
            ?: DEFAULT_READING_LEVEL
        val exampleType = findPreferenceValue(preferences, "example_type")
            ?: DEFAULT_EXAMPLE_TYPE

        return TemplateContext().apply {
            set("explanation_style", explanationStyle)
            set("reading_level", readingLevel)
            set("example_type", exampleType)
            set("has_preferences", preferences != null && preferences.isNotEmpty())
        }
    }

    /**
     * Builds the template context for the user prompt with chunks and question.
     *
     * Each chunk is converted to a map with keys matching the template
     * variables: `title`, `body`, and `topic_path`.
     */
    private fun buildChunkContext(
        question: String,
        chunks: List<RetrievalResult>,
    ): TemplateContext {
        val chunkMaps = chunks.map { result ->
            mapOf(
                "title" to "${result.chunk.title} [${result.chunk.topicPath}]",
                "body" to result.chunk.content.trim(),
                "topic_path" to result.chunk.topicPath,
            )
        }

        return TemplateContext().apply {
            set("question", question)
            setList("chunks", chunkMaps)
        }
    }

    // ── ChatML formatting ───────────────────────────────────────────

    /**
     * Wraps system and user prompts in Qwen2.5 ChatML format.
     *
     * Qwen2.5-Instruct models expect this specific template format.
     * The trailing `<|im_start|>assistant\n` signals the model to begin
     * generating its response.
     */
    private fun formatAsChatMl(systemPrompt: String, userPrompt: String): String {
        return buildString {
            append("${IM_START}system\n")
            append(systemPrompt.trimEnd())
            append("${IM_END}\n")
            append("${IM_START}user\n")
            append(userPrompt.trim())
            append("${IM_END}\n")
            append("${IM_START}assistant\n")
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Formats a chunk's content for token estimation.
     *
     * Mirrors the format used in the user prompt template so the
     * estimation accurately reflects the actual rendered output.
     */
    private fun formatChunkForEstimation(result: RetrievalResult): String {
        return "---\n${result.chunk.title} [${result.chunk.topicPath}]:\n${result.chunk.content.trim()}\n"
    }

    /**
     * Truncates a chunk's content to fit within a token budget.
     *
     * Cuts the content at a word boundary to avoid broken words,
     * and appends "[truncated]" to signal incomplete content.
     *
     * @return A new [RetrievalResult] with truncated content, or null if
     *         the chunk can't be meaningfully truncated.
     */
    private fun truncateChunkContent(
        result: RetrievalResult,
        availableTokens: Int,
    ): RetrievalResult? {
        // Estimate how many words we can keep (tokens ÷ 1.3 ≈ words)
        val titleOverhead = estimateTokenCount(
            "---\n${result.chunk.title} [${result.chunk.topicPath}]:\n[truncated]\n",
        )
        val wordsForContent = ((availableTokens - titleOverhead) / WORDS_TO_TOKENS_FACTOR).toInt()

        if (wordsForContent <= MINIMUM_USEFUL_WORDS) return null

        val contentWords = result.chunk.content.trim().split(WHITESPACE_PATTERN)
        val truncatedContent = contentWords.take(wordsForContent).joinToString(" ") + "\n[truncated]"

        return RetrievalResult(
            chunkId = result.chunkId,
            score = result.score,
            chunk = result.chunk.copy(content = truncatedContent),
        )
    }

    /**
     * Finds a preference value by key from the learner's preference list.
     *
     * @return The preference value, or null if not found or preferences is null.
     */
    private fun findPreferenceValue(
        preferences: List<LearnerPreference>?,
        key: String,
    ): String? {
        return preferences?.find { it.key == key }?.value
    }

    /**
     * Normalizes whitespace in rendered template output.
     *
     * Template conditionals can produce runs of blank lines for non-matching
     * branches. This collapses those into single blank lines for cleaner
     * prompts without changing semantic content.
     */
    private fun normalizeWhitespace(text: String): String {
        return text
            .replace(EXCESSIVE_NEWLINES_PATTERN, "\n\n")
            .trim()
    }

    companion object InternalConstants {
        /** Minimum token count for a chunk to be worth including truncated. */
        private const val MINIMUM_USEFUL_CHUNK_TOKENS = 30

        /** Minimum words for a truncated chunk to be meaningful. */
        private const val MINIMUM_USEFUL_WORDS = 10

        /** Pattern for splitting text into words. */
        private val WHITESPACE_PATTERN = Regex("\\s+")

        /** Pattern for collapsing excessive newline sequences. */
        private val EXCESSIVE_NEWLINES_PATTERN = Regex("\n{3,}")
    }
}
