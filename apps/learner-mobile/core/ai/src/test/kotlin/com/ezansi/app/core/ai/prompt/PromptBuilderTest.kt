package com.ezansi.app.core.ai.prompt

import com.ezansi.app.core.ai.prompt.templates.DefaultTemplates
import com.ezansi.app.core.ai.retrieval.RetrievalResult
import com.ezansi.app.core.data.ContentChunk
import com.ezansi.app.core.data.LearnerPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PromptBuilder] — the enhanced data-driven prompt builder.
 *
 * Tests cover:
 * - Basic prompt construction with default preferences
 * - Preference injection (explanation style, reading level, example type)
 * - Token budget management and chunk truncation
 * - Qwen2.5 ChatML format compliance
 * - Gemma 4 turn format compliance
 * - Backward compatibility (default format is QWEN_CHATML)
 * - Grounding instruction always present (AI-10)
 * - Edge cases (empty chunks, no preferences, very long content)
 *
 * These tests validate the prompt builder in isolation. No Android
 * dependencies, no LLM inference, no content pack access.
 */
class PromptBuilderTest {

    private lateinit var builder: PromptBuilder

    @Before
    fun setUp() {
        builder = PromptBuilder()
    }

    // ── ChatML format ───────────────────────────────────────────────

    @Test
    fun `prompt contains ChatML system block`() {
        val prompt = buildDefaultPrompt()
        assertTrue(
            "Prompt must start with <|im_start|>system",
            prompt.contains("<|im_start|>system\n"),
        )
        assertTrue(
            "Prompt must contain <|im_end|> after system block",
            prompt.contains("<|im_end|>"),
        )
    }

    @Test
    fun `prompt contains ChatML user block`() {
        val prompt = buildDefaultPrompt()
        assertTrue(
            "Prompt must contain <|im_start|>user",
            prompt.contains("<|im_start|>user\n"),
        )
    }

    @Test
    fun `prompt ends with ChatML assistant start`() {
        val prompt = buildDefaultPrompt()
        assertTrue(
            "Prompt must end with <|im_start|>assistant\\n",
            prompt.endsWith("<|im_start|>assistant\n"),
        )
    }

    @Test
    fun `prompt has correct ChatML structure order`() {
        val prompt = buildDefaultPrompt()
        val systemStart = prompt.indexOf("<|im_start|>system")
        val userStart = prompt.indexOf("<|im_start|>user")
        val assistantStart = prompt.indexOf("<|im_start|>assistant")

        assertTrue("System block must come before user", systemStart < userStart)
        assertTrue("User block must come before assistant", userStart < assistantStart)
    }

    // ── Grounding enforcement (AI-10) ───────────────────────────────

    @Test
    fun `prompt always includes grounding instruction`() {
        val prompt = buildDefaultPrompt()
        assertTrue(
            "Grounding instruction must always be present (AI-10)",
            prompt.contains("Use ONLY the curriculum content provided"),
        )
    }

    @Test
    fun `prompt includes grounding even with no preferences`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 2+2?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Grounding must be present even without preferences",
            prompt.contains("Use ONLY the curriculum content provided"),
        )
    }

    @Test
    fun `prompt includes grounding even with empty chunks`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 2+2?",
            retrievedChunks = emptyList(),
            preferences = null,
        )
        assertTrue(
            "Grounding must be present even with no chunks",
            prompt.contains("Use ONLY the curriculum content provided"),
        )
    }

    @Test
    fun `grounding instruction contains all required rules`() {
        val prompt = buildDefaultPrompt()
        assertTrue(prompt.contains("Never make up mathematical facts"))
        assertTrue(prompt.contains("say so honestly"))
        assertTrue(prompt.contains("mathematical notation"))
    }

    // ── Preference injection ────────────────────────────────────────

    @Test
    fun `step-by-step preference injects numbered steps instruction`() {
        val prompt = buildPromptWithPreference("explanation_style", "step-by-step")
        assertTrue(
            "Step-by-step preference must inject step instruction",
            prompt.contains("numbered steps"),
        )
    }

    @Test
    fun `visual preference injects visual instruction`() {
        val prompt = buildPromptWithPreference("explanation_style", "visual")
        assertTrue(
            "Visual preference must inject visual instruction",
            prompt.contains("diagrams") || prompt.contains("visual representations"),
        )
    }

    @Test
    fun `simple preference injects simple language instruction`() {
        val prompt = buildPromptWithPreference("explanation_style", "simple")
        assertTrue(
            "Simple preference must inject simplicity instruction",
            prompt.contains("simplest words"),
        )
    }

    @Test
    fun `detailed preference injects thorough instruction`() {
        val prompt = buildPromptWithPreference("explanation_style", "detailed")
        assertTrue(
            "Detailed preference must inject detailed instruction",
            prompt.contains("thorough") || prompt.contains("detailed"),
        )
    }

    @Test
    fun `simple reading level injects grade 4-5 vocabulary instruction`() {
        val prompt = buildPromptWithPreference("reading_level", "simple")
        assertTrue(
            "Simple reading level must reference Grade 4-5 vocabulary",
            prompt.contains("Grade 4-5"),
        )
    }

    @Test
    fun `advanced reading level injects grade 7-8 vocabulary instruction`() {
        val prompt = buildPromptWithPreference("reading_level", "advanced")
        assertTrue(
            "Advanced reading level must reference Grade 7-8 vocabulary",
            prompt.contains("Grade 7-8"),
        )
    }

    @Test
    fun `everyday example type injects South African context`() {
        val prompt = buildPromptWithPreference("example_type", "everyday")
        assertTrue(
            "Everyday examples must reference South African context",
            prompt.contains("South African") || prompt.contains("Rand"),
        )
    }

    @Test
    fun `abstract example type injects mathematical examples instruction`() {
        val prompt = buildPromptWithPreference("example_type", "abstract")
        assertTrue(
            "Abstract examples must reference numbers and symbols",
            prompt.contains("abstract") || prompt.contains("numbers and symbols"),
        )
    }

    @Test
    fun `null preferences use defaults`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        // Default is step-by-step, so should contain numbered steps
        assertTrue(
            "Null preferences should use step-by-step default",
            prompt.contains("numbered steps"),
        )
    }

    @Test
    fun `empty preferences list uses defaults`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = emptyList(),
        )
        assertTrue(
            "Empty preferences should use step-by-step default",
            prompt.contains("numbered steps"),
        )
    }

    // ── Content chunks ──────────────────────────────────────────────

    @Test
    fun `prompt includes chunk content`() {
        val chunks = listOf(
            createChunk("ch1", "Adding Fractions", "To add fractions, find the LCD."),
        )
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "How do I add fractions?",
            retrievedChunks = chunks,
            preferences = null,
        )
        assertTrue("Prompt must include chunk title", prompt.contains("Adding Fractions"))
        assertTrue("Prompt must include chunk content", prompt.contains("find the LCD"))
    }

    @Test
    fun `prompt includes multiple chunks`() {
        val chunks = listOf(
            createChunk("ch1", "Chunk One", "Content one"),
            createChunk("ch2", "Chunk Two", "Content two"),
            createChunk("ch3", "Chunk Three", "Content three"),
        )
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test",
            retrievedChunks = chunks,
            preferences = null,
        )
        assertTrue(prompt.contains("Chunk One"))
        assertTrue(prompt.contains("Chunk Two"))
        assertTrue(prompt.contains("Chunk Three"))
    }

    @Test
    fun `prompt includes chunk topic path`() {
        val chunks = listOf(
            createChunk(
                "ch1",
                "Fractions",
                "Content",
                topicPath = "term1.week3.fractions.addition",
            ),
        )
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test",
            retrievedChunks = chunks,
            preferences = null,
        )
        assertTrue(
            "Prompt must include topic path for attribution",
            prompt.contains("term1.week3.fractions.addition"),
        )
    }

    @Test
    fun `prompt includes learner question in user block`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        // Question should appear in the user section (after <|im_start|>user)
        val userSection = prompt.substringAfter("<|im_start|>user\n")
        assertTrue(
            "User section must contain the learner's question",
            userSection.contains("What is 1/2 + 1/4?"),
        )
    }

    @Test
    fun `empty chunks list still produces valid prompt`() {
        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = emptyList(),
            preferences = null,
        )
        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("<|im_start|>user"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    // ── Token budget management ─────────────────────────────────────

    @Test
    fun `estimateTokenCount returns reasonable estimates`() {
        // "Hello world" = 2 words × 1.3 = 2.6 → 2 tokens
        val estimate = builder.estimateTokenCount("Hello world")
        assertTrue("Estimate should be reasonable", estimate in 1..5)
    }

    @Test
    fun `estimateTokenCount returns 0 for blank text`() {
        assertTrue(builder.estimateTokenCount("") == 0)
        assertTrue(builder.estimateTokenCount("   ") == 0)
    }

    @Test
    fun `estimateTokenCount returns at least 1 for non-blank text`() {
        assertTrue(builder.estimateTokenCount("x") >= 1)
    }

    @Test
    fun `chunks are truncated when exceeding token budget`() {
        // Create chunks with very long content that would exceed the budget
        val longContent = (1..500).joinToString(" ") { "word$it" }
        val chunks = listOf(
            createChunk("ch1", "First", longContent, score = 0.9f),
            createChunk("ch2", "Second", longContent, score = 0.8f),
            createChunk("ch3", "Third", longContent, score = 0.7f),
        )

        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test",
            retrievedChunks = chunks,
            preferences = null,
        )

        // The prompt should fit within a reasonable size
        val estimatedTokens = builder.estimateTokenCount(prompt)
        assertTrue(
            "Prompt input should fit within max input tokens ($estimatedTokens tokens)",
            estimatedTokens <= PromptBuilder.MAX_PROMPT_INPUT_TOKENS + 50, // small margin for estimation imprecision
        )
    }

    @Test
    fun `higher-scored chunks are preserved over lower-scored ones`() {
        val longContent = (1..300).joinToString(" ") { "word$it" }
        val chunks = listOf(
            createChunk("ch1", "High Score", longContent, score = 0.95f),
            createChunk("ch2", "Medium Score", longContent, score = 0.80f),
            createChunk("ch3", "Low Score", longContent, score = 0.60f),
        )

        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test",
            retrievedChunks = chunks,
            preferences = null,
        )

        // High score chunk should always be present
        assertTrue("Highest-scored chunk must be preserved", prompt.contains("High Score"))
    }

    // ── System prompt rendering ─────────────────────────────────────

    @Test
    fun `system prompt contains eZansi personality`() {
        val systemPrompt = builder.renderSystemPrompt(null)
        assertTrue(
            "System prompt must identify the tutor as eZansi",
            systemPrompt.contains("eZansi"),
        )
    }

    @Test
    fun `system prompt mentions Grade 6`() {
        val systemPrompt = builder.renderSystemPrompt(null)
        assertTrue(
            "System prompt must reference Grade 6",
            systemPrompt.contains("Grade 6"),
        )
    }

    @Test
    fun `system prompt mentions South African`() {
        val systemPrompt = builder.renderSystemPrompt(null)
        assertTrue(
            "System prompt must reference South African context",
            systemPrompt.contains("South African"),
        )
    }

    // ── Integration with DefaultTemplates ───────────────────────────

    @Test
    fun `default system prompt template is valid Jinja2`() {
        // Should not throw when rendering
        val engine = TemplateEngine()
        val result = engine.render(
            DefaultTemplates.SYSTEM_PROMPT,
            mapOf(
                "explanation_style" to "step-by-step",
                "reading_level" to "simple",
                "example_type" to "everyday",
            ),
        )
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `default user prompt template is valid Jinja2`() {
        val engine = TemplateEngine()
        val result = engine.render(
            DefaultTemplates.USER_PROMPT,
            mapOf(
                "question" to "Test?",
                "chunks" to listOf(
                    mapOf("title" to "Title", "body" to "Body"),
                ),
            ),
        )
        assertTrue(result.contains("Title"))
        assertTrue(result.contains("Body"))
        assertTrue(result.contains("Test?"))
    }

    @Test
    fun `grounding instruction is a non-empty string`() {
        assertTrue(
            "Grounding instruction must be non-empty",
            DefaultTemplates.GROUNDING_INSTRUCTION.isNotBlank(),
        )
    }

    // ── Full pipeline integration ───────────────────────────────────

    @Test
    fun `full prompt construction produces valid output`() {
        val chunks = listOf(
            createChunk(
                "ch1",
                "Adding Fractions",
                "To add fractions with different denominators, find the LCD. " +
                    "Example: 1/2 + 1/4 = 2/4 + 1/4 = 3/4.",
                topicPath = "term1.week3.fractions.addition",
                score = 0.92f,
            ),
            createChunk(
                "ch2",
                "Finding LCD",
                "The Lowest Common Denominator (LCD) is the smallest number " +
                    "that both denominators divide into evenly.",
                topicPath = "term1.week3.fractions.lcd",
                score = 0.85f,
            ),
        )

        val preferences = listOf(
            LearnerPreference("explanation_style", "step-by-step", "Style", "Explanation style"),
            LearnerPreference("reading_level", "simple", "Level", "Reading level"),
            LearnerPreference("example_type", "everyday", "Examples", "Example type"),
        )

        val prompt = builder.buildGroundedPromptFromRetrievedChunks(
            question = "How do I add 1/2 and 1/4?",
            retrievedChunks = chunks,
            preferences = preferences,
        )

        // Structure checks
        assertTrue(prompt.startsWith("<|im_start|>system\n"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))

        // Content checks
        assertTrue(prompt.contains("eZansi"))
        assertTrue(prompt.contains("numbered steps"))
        assertTrue(prompt.contains("Grade 4-5"))
        assertTrue(prompt.contains("South African"))
        assertTrue(prompt.contains("Adding Fractions"))
        assertTrue(prompt.contains("find the LCD"))
        assertTrue(prompt.contains("How do I add 1/2 and 1/4?"))

        // Grounding check
        assertTrue(prompt.contains("Use ONLY the curriculum content"))
        assertTrue(prompt.contains("Never make up mathematical facts"))
    }

    // ── Gemma 4 turn format ────────────────────────────────────────

    @Test
    fun `gemma format produces start_of_turn delimiter`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Gemma format must contain <start_of_turn>",
            prompt.contains("<start_of_turn>"),
        )
    }

    @Test
    fun `gemma format produces end_of_turn delimiter`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Gemma format must contain <end_of_turn>",
            prompt.contains("<end_of_turn>"),
        )
    }

    @Test
    fun `gemma format has no separate system role`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertFalse(
            "Gemma format must NOT contain <start_of_turn>system",
            prompt.contains("<start_of_turn>system"),
        )
        assertFalse(
            "Gemma format must NOT contain ChatML delimiters",
            prompt.contains("<|im_start|>"),
        )
    }

    @Test
    fun `gemma format system prompt is inside user turn`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        // System content (eZansi personality) should appear after <start_of_turn>user
        // and before <end_of_turn>, i.e. inside the user turn
        val userTurn = prompt.substringAfter("<start_of_turn>user\n")
            .substringBefore("<end_of_turn>")
        assertTrue(
            "System prompt (eZansi personality) must be inside user turn",
            userTurn.contains("eZansi"),
        )
        assertTrue(
            "Grounding instruction must be inside user turn",
            userTurn.contains("Use ONLY the curriculum content provided"),
        )
    }

    @Test
    fun `gemma format ends with start_of_turn model`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Gemma format must end with <start_of_turn>model\\n",
            prompt.endsWith("<start_of_turn>model\n"),
        )
    }

    @Test
    fun `gemma format includes grounding instruction`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 2+2?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Grounding instruction must be present in Gemma format (AI-10)",
            prompt.contains("Use ONLY the curriculum content provided"),
        )
        assertTrue(
            "Grounding rules must be present in Gemma format",
            prompt.contains("Never make up mathematical facts"),
        )
    }

    @Test
    fun `gemma format includes chunk content and question`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val chunks = listOf(
            createChunk("ch1", "Adding Fractions", "To add fractions, find the LCD."),
        )
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "How do I add fractions?",
            retrievedChunks = chunks,
            preferences = null,
        )
        assertTrue("Gemma prompt must include chunk title", prompt.contains("Adding Fractions"))
        assertTrue("Gemma prompt must include chunk content", prompt.contains("find the LCD"))
        assertTrue("Gemma prompt must include question", prompt.contains("How do I add fractions?"))
    }

    @Test
    fun `gemma format has correct structure order`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        val userStart = prompt.indexOf("<start_of_turn>user")
        val endOfTurn = prompt.indexOf("<end_of_turn>")
        val modelStart = prompt.indexOf("<start_of_turn>model")

        assertTrue("User turn must come before end_of_turn", userStart < endOfTurn)
        assertTrue("end_of_turn must come before model turn", endOfTurn < modelStart)
    }

    @Test
    fun `gemma format with empty chunks still produces valid prompt`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = emptyList(),
            preferences = null,
        )
        assertTrue(prompt.contains("<start_of_turn>user"))
        assertTrue(prompt.contains("<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    // ── Backward compatibility ──────────────────────────────────────

    @Test
    fun `default format is QWEN_CHATML for backward compatibility`() {
        // PromptBuilder() with no chatFormat argument should use QWEN_CHATML
        val defaultBuilder = PromptBuilder()
        val prompt = defaultBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 2+2?",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertTrue(
            "Default format must be QWEN_CHATML",
            prompt.contains("<|im_start|>system"),
        )
        assertFalse(
            "Default format must NOT contain Gemma delimiters",
            prompt.contains("<start_of_turn>"),
        )
    }

    @Test
    fun `qwen format does not contain gemma delimiters`() {
        val qwenBuilder = PromptBuilder(chatFormat = ChatFormat.QWEN_CHATML)
        val prompt = qwenBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertFalse(
            "Qwen format must NOT contain <start_of_turn>",
            prompt.contains("<start_of_turn>"),
        )
        assertFalse(
            "Qwen format must NOT contain <end_of_turn>",
            prompt.contains("<end_of_turn>"),
        )
    }

    @Test
    fun `gemma format does not contain qwen delimiters`() {
        val gemmaBuilder = PromptBuilder(chatFormat = ChatFormat.GEMMA_TURN)
        val prompt = gemmaBuilder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = null,
        )
        assertFalse(
            "Gemma format must NOT contain <|im_start|>",
            prompt.contains("<|im_start|>"),
        )
        assertFalse(
            "Gemma format must NOT contain <|im_end|>",
            prompt.contains("<|im_end|>"),
        )
    }

    // ── Test helpers ────────────────────────────────────────────────

    private fun buildDefaultPrompt(): String {
        return builder.buildGroundedPromptFromRetrievedChunks(
            question = "What is 1/2 + 1/4?",
            retrievedChunks = createTestChunks(2),
            preferences = null,
        )
    }

    private fun buildPromptWithPreference(key: String, value: String): String {
        return builder.buildGroundedPromptFromRetrievedChunks(
            question = "Test question",
            retrievedChunks = createTestChunks(1),
            preferences = listOf(
                LearnerPreference(key, value, "Display", "Description"),
            ),
        )
    }

    private fun createTestChunks(count: Int): List<RetrievalResult> {
        return (1..count).map { i ->
            createChunk(
                chunkId = "chunk-$i",
                title = "Test Chunk $i",
                content = "This is test content for chunk $i about Grade 6 maths.",
                score = 1.0f - (i * 0.1f),
            )
        }
    }

    private fun createChunk(
        chunkId: String = "ch-1",
        title: String = "Test Title",
        content: String = "Test content",
        topicPath: String = "term1.test.topic",
        score: Float = 0.9f,
    ): RetrievalResult {
        return RetrievalResult(
            chunkId = chunkId,
            score = score,
            chunk = ContentChunk(
                chunkId = chunkId,
                packId = "test-pack",
                title = title,
                topicPath = topicPath,
                content = content,
            ),
        )
    }
}
