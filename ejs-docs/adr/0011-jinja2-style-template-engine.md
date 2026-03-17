---
ejs:
  type: journey-adr
  version: 1.1
  adr_id: "0011"
  title: Custom Jinja2-Style Prompt Template Engine
  date: 2026-03-17
  status: accepted
  session_id: ejs-session-2026-03-17-01
  session_journey: ejs-docs/journey/2026/ejs-session-2026-03-17-01.md

actors:
  humans:
    - id: McFuzzySquirrel
      role: lead developer
  agents:
    - id: ai-pipeline-engineer
      role: template engine design and implementation
    - id: qa-test-engineer
      role: 98 unit tests for template engine

context:
  repo: ezansiedgeai-small
  branch: agent-forge/build-agent-team
---

# Session Journey

- Session Journey: `ejs-docs/journey/2026/ejs-session-2026-03-17-01.md`

# Context

The AI pipeline constructs grounded prompts from system instructions, retrieved content chunks, and learner preferences. Prompts must be data-driven (not hardcoded) so that:
- Changing learner preferences (explanation style, reading level) changes the generated explanation
- Content packs can potentially ship custom prompt templates in future
- The grounding instruction ("Use only the provided content") cannot be accidentally omitted

The template engine renders prompts within a strict 2,048-token context window (system ~200, user content ~1,300, generation 500, overhead 48).

Key constraints:
- No `eval()`, no reflection, no code execution in templates (security)
- Zero external dependencies (offline, minimal APK)
- Must support conditionals (preferences vary per learner)
- Must support loops (multiple retrieved chunks)
- Grounding instruction must be architecturally separated from templates

---

# Session Intent

Build a template rendering system that constructs LLM prompts from learner preferences and retrieved content, with security guarantees and grounding enforcement.

# Collaboration Summary

The ai-pipeline-engineer agent designed and implemented the template engine during P1-107. It chose Jinja2 syntax for familiarity and built a pure recursive-descent parser. The qa-test-engineer agent wrote 98 unit tests (later folded into the 285 test total). The grounding enforcement architecture — separating grounding instructions from renderable templates — was a key design insight.

---

# Decision Trigger / Significance

The template engine is a custom 784-line component at the heart of the AI pipeline. Every LLM prompt passes through it. Its syntax, security model, and grounding enforcement pattern affect prompt quality, security, and maintainability. Building a custom parser (vs using a library) is a significant technical commitment.

# Considered Options

## Option A: String concatenation / String.format

Simple string building with placeholder substitution.

- **Pros:** Zero complexity, no parser needed
- **Cons:** No conditionals (can't adapt to preferences), no loops (can't iterate chunks), maintenance nightmare for multi-section prompts, no escaping

## Option B: Mustache / Handlebars library

Logic-less template library with `{{variable}}` syntax.

- **Pros:** Well-known syntax, existing libraries available
- **Cons:** No conditionals (Mustache is logic-less), Handlebars adds helpers but requires a dependency (~500 KB), neither supports the grounding separation pattern

## Option C: Kotlin string templates with helper functions

Use Kotlin's `${}` interpolation with builder DSL.

- **Pros:** Native Kotlin, no parser needed, type-safe
- **Cons:** Templates are compiled code (not data-driven), can't ship templates in content packs, harder to test prompt variations, no grounding separation

## Option D: Custom Jinja2-style engine (Selected)

Pure recursive-descent parser supporting `{{ var }}`, `{% if %}`, `{% for %}`, and filters. No eval, no reflection.

- **Pros:** Familiar syntax (Jinja2 is widely known), full conditionals and loops, templates are data (not code), grounding separation enforced architecturally, zero dependencies
- **Cons:** 784 lines of custom parser code, must be maintained, potential for parser bugs

---

# Decision

Build a custom Jinja2-style template engine using a pure recursive-descent parser. Templates are plain text with `{{ }}` for interpolation, `{% if/elif/else/endif %}` for conditionals, `{% for/endfor %}` for loops, and `|` for filters.

**Key files:**
- `core/ai/src/main/kotlin/com/ezansi/app/core/ai/prompt/TemplateEngine.kt` — the parser and renderer (~784 lines)
- `core/ai/src/main/kotlin/com/ezansi/app/core/ai/prompt/TemplateContext.kt` — rendering context model
- `core/ai/src/main/kotlin/com/ezansi/app/core/ai/prompt/DefaultTemplates.kt` — bundled prompt templates
- `core/ai/src/main/kotlin/com/ezansi/app/core/ai/prompt/PromptBuilder.kt` — token-budget-aware prompt assembly

**Supported syntax:**
- Variables: `{{ learner_name }}`, `{{ preferences.style }}`
- Conditionals: `{% if reading_level == "basic" %}...{% elif %}...{% else %}...{% endif %}`
- Loops: `{% for chunk in chunks %}...{% endfor %}`
- Filters: `{{ name | upper }}`, `{{ text | truncate(100) }}`, `{{ val | default("fallback") }}`
- Available filters: `upper`, `lower`, `truncate`, `default`, `trim`, `capitalize`

**Grounding enforcement:** The grounding instruction is appended by `PromptBuilder` *after* template rendering. Templates cannot omit or override it. This is an architectural guarantee, not a convention.

---

# Rationale

1. **Security:** No `eval()`, no reflection, no code execution. Templates cannot escape the rendering sandbox. This is critical because future content packs may ship custom templates.
2. **Data-driven:** Templates are strings, not compiled code. They can be stored in content packs, modified without recompilation, and A/B tested.
3. **Familiar syntax:** Jinja2 is one of the most widely known template syntaxes. New contributors can read and modify templates without learning a custom DSL.
4. **Grounding guarantee:** The architectural separation between templates and grounding instructions means it's impossible to accidentally (or deliberately) omit the "Use only the provided content" instruction. PromptBuilder always appends it after rendering.
5. **Zero dependencies:** The parser is pure Kotlin with no external libraries. No APK size impact.
6. **Thorough testing:** 98 unit tests (later part of 285 total) cover syntax parsing, filter application, error handling, nested conditionals, and edge cases. This mitigates the risk of maintaining a custom parser.

The team accepted 784 lines of custom parser code because the alternatives either lacked required features (conditionals, loops) or introduced dependencies.

---

# Consequences

### Positive
- Templates are human-readable and editable by non-developers (curriculum specialists)
- Grounding instruction cannot be accidentally omitted
- Zero APK size impact
- 98 unit tests provide high confidence in correctness
- Future content packs can potentially ship custom prompt templates

### Negative / Trade-offs
- 784 lines of custom parser code to maintain
- New syntax features (e.g., macros, includes) require parser modifications
- Parser error messages could be more descriptive (currently basic)
- No IDE syntax highlighting for templates (they're plain strings)

---

# Key Learnings

Recursive-descent parsing in Kotlin is straightforward for simple grammars. The key insight is separating the tokeniser (splitting `{{ }}` and `{% %}` tags) from the renderer (evaluating the AST). Filter support via the pipe operator adds significant usability with minimal parser complexity.

The grounding separation pattern — PromptBuilder appends grounding *after* template rendering — is more robust than including grounding in the template itself. It converts a convention ("don't forget the grounding block") into an architectural guarantee.

---

# Agent Guidance

- **Do not build another template engine** — TemplateEngine.kt is the single implementation
- **Add new filters** by extending the `applyFilter()` function in TemplateEngine.kt
- **DefaultTemplates.kt** contains the bundled prompt templates — modify these to change prompt style
- **PromptBuilder.kt** handles token budgeting — it truncates chunks to fit the 2,048-token window
- **Grounding is always appended by PromptBuilder** — never put grounding instructions inside templates
- **Test new templates** by adding cases to TemplateEngineTest.kt

---

# Reuse Signals (Optional)

```yaml
reuse:
  patterns:
    - "Recursive-descent parser for simple template grammars"
    - "Architectural separation of grounding from renderable templates"
    - "Token-budget-aware prompt assembly"
  prompts:
    - "Add a new filter to TemplateEngine.applyFilter()"
    - "Create a new prompt template in DefaultTemplates.kt"
  anti_patterns:
    - "Do not embed grounding instructions inside templates"
    - "Do not use eval() or reflection in template rendering"
    - "Do not build a second template engine"
  future_considerations:
    - "Content pack custom templates (load from pack SQLite instead of DefaultTemplates)"
    - "Template validation CLI tool for content authors"
    - "More descriptive parser error messages with line/column numbers"
```
