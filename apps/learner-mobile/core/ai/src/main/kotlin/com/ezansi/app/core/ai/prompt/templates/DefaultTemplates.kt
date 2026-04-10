package com.ezansi.app.core.ai.prompt.templates

/**
 * Bundled default prompt templates for the eZansi explanation engine.
 *
 * These templates are used when the active content pack does not include
 * its own custom templates. They define the personality, grounding rules,
 * and structure of the prompts sent to the on-device LLM.
 *
 * ## Supported Models
 *
 * The template **content** is model-agnostic — the same personality,
 * preference conditionals, and grounding rules are used for both:
 * - **Qwen2.5-1.5B** (legacy, via llama.cpp)
 * - **Gemma 4 1B** (primary, via MediaPipe LiteRT)
 *
 * The chat-format wrapping (ChatML vs Gemma turn delimiters) is handled
 * by [PromptBuilder][com.ezansi.app.core.ai.prompt.PromptBuilder] via
 * [ChatFormat][com.ezansi.app.core.ai.prompt.ChatFormat], not by these
 * templates.
 *
 * ## Template Architecture
 *
 * The prompt is assembled from three template components:
 *
 * 1. **[SYSTEM_PROMPT]** — Tutor personality, preference-based style instructions,
 *    and grounding rules. Rendered once per query. (~200 tokens)
 * 2. **[USER_PROMPT]** — Retrieved curriculum content and the learner's question.
 *    Content is dynamically sized to fit the token budget. (~1,300 tokens max)
 * 3. **[GROUNDING_INSTRUCTION]** — The grounding enforcement block that prevents
 *    hallucination. Always appended to the system prompt — never omitted.
 *
 * ## Grounding Enforcement (AI-10)
 *
 * The grounding instruction is architecturally separated from the system prompt
 * so it can never be accidentally removed when customising the personality.
 * The [PromptBuilder][com.ezansi.app.core.ai.prompt.PromptBuilder] always
 * appends [GROUNDING_INSTRUCTION] to the rendered system prompt.
 *
 * ## Template Syntax
 *
 * Templates use Jinja2-style syntax processed by
 * [TemplateEngine][com.ezansi.app.core.ai.prompt.TemplateEngine]:
 * - `{{ variable }}` — Variable substitution
 * - `{% if %}...{% elif %}...{% else %}...{% endif %}` — Conditionals
 * - `{% for item in list %}...{% endfor %}` — Loops
 * - `{{ value | filter }}` — Output filters
 *
 * @see com.ezansi.app.core.ai.prompt.TemplateEngine
 * @see com.ezansi.app.core.ai.prompt.PromptBuilder
 * @see com.ezansi.app.core.ai.prompt.ChatFormat
 */
object DefaultTemplates {

    /**
     * System prompt template — defines the tutor personality and style.
     *
     * Adapts dynamically based on learner preferences:
     * - `explanation_style`: step-by-step, visual, simple, or detailed
     * - `reading_level`: simple or advanced
     * - `example_type`: everyday, abstract, or visual
     *
     * The grounding instruction is NOT part of this template — it is
     * appended separately by [PromptBuilder] to ensure it is always present.
     */
    val SYSTEM_PROMPT: String = """
You are eZansi, a friendly and patient maths tutor for Grade 6 South African learners.
{% if explanation_style == "step-by-step" %}
Always break down your explanation into numbered steps.
{% elif explanation_style == "visual" %}
Use diagrams described in text, tables, and visual representations wherever possible.
{% elif explanation_style == "simple" %}
Use the simplest words possible. Keep sentences very short.
{% elif explanation_style == "detailed" %}
Give thorough, detailed explanations with multiple examples.
{% endif %}
{% if reading_level == "simple" %}
Use Grade 4-5 vocabulary. Avoid complex words.
{% elif reading_level == "advanced" %}
You may use Grade 7-8 vocabulary where helpful.
{% endif %}
{% if example_type == "everyday" %}
Use real-life South African examples: sharing food equally, measuring for building, counting money in Rand.
{% elif example_type == "abstract" %}
Use abstract mathematical examples with numbers and symbols.
{% elif example_type == "visual" %}
Describe visual representations: number lines, fraction bars, pie charts.
{% endif %}
""".trimStart()

    /**
     * User prompt template — presents the retrieved content and question.
     *
     * The `chunks` list contains maps with keys:
     * - `title` — Display title of the content chunk
     * - `body` — The chunk's Markdown content
     * - `topic_path` — CAPS topic path (e.g. "term1.week3.fractions")
     *
     * The `question` variable contains the learner's natural-language question.
     */
    val USER_PROMPT: String = """
CURRICULUM CONTENT (verified, CAPS-aligned — use ONLY this):
{% for chunk in chunks %}
---
{{ chunk.title }}:
{{ chunk.body }}
{% endfor %}
---

LEARNER'S QUESTION: {{ question }}
""".trimStart()

    /**
     * Grounding enforcement instruction — always appended to the system prompt.
     *
     * This block prevents hallucination by instructing the model to use
     * only the curriculum content provided in the user turn. It is
     * architecturally separated from [SYSTEM_PROMPT] so that:
     * 1. Custom templates cannot accidentally omit it
     * 2. Content pack authors can customise personality without
     *    weakening grounding
     * 3. Every generated explanation is verifiable against source chunks
     *
     * @see AI-10 in the PRD for grounding requirements
     */
    val GROUNDING_INSTRUCTION: String = """
IMPORTANT RULES:
- Use ONLY the curriculum content provided below to answer
- If the content doesn't cover the question, say so honestly
- Never make up mathematical facts or formulas
- Use proper mathematical notation
""".trimStart().trimEnd()
}
