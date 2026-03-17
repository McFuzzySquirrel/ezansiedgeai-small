package com.ezansi.app.core.ai.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TemplateEngine] — the Jinja2-style template renderer.
 *
 * Tests cover all supported syntax features:
 * - Variable substitution (simple, dot-notation, missing)
 * - Conditionals (if, elif, else, nested)
 * - Loops (for/endfor, empty lists, nested maps)
 * - Filters (upper, lower, truncate, default, trim, capitalize)
 * - Whitespace control ({%- -%}, {{- -}})
 * - Comments ({# ... #})
 * - Error handling (unclosed tags, unknown filters, malformed syntax)
 *
 * These tests validate the template engine in isolation — no Android
 * dependencies, no LLM inference, no content pack access.
 */
class TemplateEngineTest {

    private lateinit var engine: TemplateEngine

    @Before
    fun setUp() {
        engine = TemplateEngine()
    }

    // ── Variable substitution ───────────────────────────────────────

    @Test
    fun `render plain text without any template syntax`() {
        val result = engine.render("Hello, world!", emptyMap())
        assertEquals("Hello, world!", result)
    }

    @Test
    fun `render empty template returns empty string`() {
        val result = engine.render("", emptyMap())
        assertEquals("", result)
    }

    @Test
    fun `render simple variable substitution`() {
        val result = engine.render(
            "Hello, {{ name }}!",
            mapOf("name" to "Thandiwe"),
        )
        assertEquals("Hello, Thandiwe!", result)
    }

    @Test
    fun `render multiple variables in one template`() {
        val result = engine.render(
            "{{ greeting }}, {{ name }}! You are in Grade {{ grade }}.",
            mapOf("greeting" to "Hello", "name" to "Sipho", "grade" to 6),
        )
        assertEquals("Hello, Sipho! You are in Grade 6.", result)
    }

    @Test
    fun `render missing variable produces empty string`() {
        val result = engine.render(
            "Hello, {{ name }}!",
            emptyMap(),
        )
        assertEquals("Hello, !", result)
    }

    @Test
    fun `render null variable produces empty string`() {
        val result = engine.render(
            "Value: {{ x }}",
            mapOf("x" to null),
        )
        assertEquals("Value: ", result)
    }

    @Test
    fun `render dot-notation variable access`() {
        val result = engine.render(
            "Title: {{ chunk.title }}",
            mapOf("chunk" to mapOf("title" to "Adding Fractions")),
        )
        assertEquals("Title: Adding Fractions", result)
    }

    @Test
    fun `render deeply nested dot-notation`() {
        val result = engine.render(
            "{{ a.b.c }}",
            mapOf("a" to mapOf("b" to mapOf("c" to "deep"))),
        )
        assertEquals("deep", result)
    }

    @Test
    fun `render dot-notation with missing intermediate key`() {
        val result = engine.render(
            "{{ a.b.c }}",
            mapOf("a" to mapOf("x" to "y")),
        )
        assertEquals("", result)
    }

    // ── Conditionals ────────────────────────────────────────────────

    @Test
    fun `if block renders body when condition is truthy`() {
        val result = engine.render(
            "{% if show %}Visible{% endif %}",
            mapOf("show" to true),
        )
        assertEquals("Visible", result)
    }

    @Test
    fun `if block skips body when condition is falsy`() {
        val result = engine.render(
            "{% if show %}Visible{% endif %}",
            mapOf("show" to false),
        )
        assertEquals("", result)
    }

    @Test
    fun `if-else block renders else when condition is falsy`() {
        val result = engine.render(
            "{% if show %}Yes{% else %}No{% endif %}",
            mapOf("show" to false),
        )
        assertEquals("No", result)
    }

    @Test
    fun `if-elif-else block renders matching elif`() {
        val template = """{% if style == "a" %}A{% elif style == "b" %}B{% elif style == "c" %}C{% else %}D{% endif %}"""
        assertEquals("B", engine.render(template, mapOf("style" to "b")))
    }

    @Test
    fun `if-elif-else block renders else when no branch matches`() {
        val template = """{% if style == "a" %}A{% elif style == "b" %}B{% else %}Default{% endif %}"""
        assertEquals("Default", engine.render(template, mapOf("style" to "z")))
    }

    @Test
    fun `if-elif block with no else renders nothing when no branch matches`() {
        val template = """{% if style == "a" %}A{% elif style == "b" %}B{% endif %}"""
        assertEquals("", engine.render(template, mapOf("style" to "z")))
    }

    @Test
    fun `string equality comparison with double quotes`() {
        val template = """{% if level == "advanced" %}Advanced{% endif %}"""
        assertEquals("Advanced", engine.render(template, mapOf("level" to "advanced")))
        assertEquals("", engine.render(template, mapOf("level" to "basic")))
    }

    @Test
    fun `string equality comparison with single quotes`() {
        val template = """{% if level == 'simple' %}Simple{% endif %}"""
        assertEquals("Simple", engine.render(template, mapOf("level" to "simple")))
    }

    @Test
    fun `string inequality comparison`() {
        val template = """{% if status != "done" %}In progress{% endif %}"""
        assertEquals("In progress", engine.render(template, mapOf("status" to "pending")))
        assertEquals("", engine.render(template, mapOf("status" to "done")))
    }

    @Test
    fun `not operator negates truthiness`() {
        val template = """{% if not empty %}Has content{% endif %}"""
        assertEquals("Has content", engine.render(template, mapOf("empty" to false)))
        assertEquals("", engine.render(template, mapOf("empty" to true)))
    }

    @Test
    fun `not operator does not match variable starting with not`() {
        val template = """{% if notification %}Alert{% endif %}"""
        assertEquals("Alert", engine.render(template, mapOf("notification" to true)))
    }

    @Test
    fun `truthiness of various types`() {
        // Truthy
        assertTrue(engine.evaluateCondition("x", mapOf("x" to true)))
        assertTrue(engine.evaluateCondition("x", mapOf("x" to "hello")))
        assertTrue(engine.evaluateCondition("x", mapOf("x" to 42)))
        assertTrue(engine.evaluateCondition("x", mapOf("x" to listOf(1))))

        // Falsy
        assertFalse(engine.evaluateCondition("x", mapOf("x" to null)))
        assertFalse(engine.evaluateCondition("x", mapOf("x" to false)))
        assertFalse(engine.evaluateCondition("x", mapOf("x" to "")))
        assertFalse(engine.evaluateCondition("x", mapOf("x" to 0)))
        assertFalse(engine.evaluateCondition("x", mapOf("x" to emptyList<Any>())))
    }

    @Test
    fun `missing variable in condition is falsy`() {
        assertFalse(engine.evaluateCondition("missing", emptyMap()))
    }

    @Test
    fun `nested if blocks`() {
        val template = """{% if a %}{% if b %}AB{% else %}A-only{% endif %}{% endif %}"""
        assertEquals("AB", engine.render(template, mapOf("a" to true, "b" to true)))
        assertEquals("A-only", engine.render(template, mapOf("a" to true, "b" to false)))
        assertEquals("", engine.render(template, mapOf("a" to false, "b" to true)))
    }

    // ── Loops ───────────────────────────────────────────────────────

    @Test
    fun `for loop iterates over list of maps`() {
        val template = "{% for item in items %}{{ item.name }} {% endfor %}"
        val context = mapOf<String, Any?>(
            "items" to listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
                mapOf("name" to "Charlie"),
            ),
        )
        assertEquals("Alice Bob Charlie ", engine.render(template, context))
    }

    @Test
    fun `for loop with empty list produces no output`() {
        val template = "Before{% for item in items %}X{% endfor %}After"
        assertEquals("BeforeAfter", engine.render(template, mapOf("items" to emptyList<Any>())))
    }

    @Test
    fun `for loop with null list produces no output`() {
        val template = "Before{% for item in items %}X{% endfor %}After"
        assertEquals("BeforeAfter", engine.render(template, mapOf("items" to null)))
    }

    @Test
    fun `for loop with missing list variable produces no output`() {
        val template = "{% for item in missing %}X{% endfor %}"
        assertEquals("", engine.render(template, emptyMap()))
    }

    @Test
    fun `for loop variable does not leak into outer scope`() {
        val template = "Before:{{ item }}-{% for item in items %}{{ item }},{% endfor %}-After:{{ item }}"
        val context = mapOf<String, Any?>(
            "items" to listOf("a", "b"),
        )
        val result = engine.render(template, context)
        assertEquals("Before:-a,b,-After:", result)
    }

    @Test
    fun `for loop preserves outer variable with same name`() {
        val template = "{% for item in items %}{{ item }},{% endfor %}{{ item }}"
        val context = mapOf<String, Any?>(
            "items" to listOf("a", "b"),
            "item" to "original",
        )
        val result = engine.render(template, context)
        assertEquals("a,b,original", result)
    }

    @Test
    fun `nested for loops`() {
        val template = "{% for row in rows %}{% for col in row.cols %}{{ col }}{% endfor %};{% endfor %}"
        val context = mapOf<String, Any?>(
            "rows" to listOf(
                mapOf("cols" to listOf("a", "b")),
                mapOf("cols" to listOf("c", "d")),
            ),
        )
        assertEquals("ab;cd;", engine.render(template, context))
    }

    @Test
    fun `for loop with if inside`() {
        val template = "{% for n in nums %}{% if n == \"2\" %}*{% else %}-{% endif %}{% endfor %}"
        val context = mapOf<String, Any?>(
            "nums" to listOf("1", "2", "3"),
        )
        assertEquals("-*-", engine.render(template, context))
    }

    // ── Filters ─────────────────────────────────────────────────────

    @Test
    fun `upper filter converts to uppercase`() {
        assertEquals("HELLO", engine.render("{{ x | upper }}", mapOf("x" to "hello")))
    }

    @Test
    fun `lower filter converts to lowercase`() {
        assertEquals("hello", engine.render("{{ x | lower }}", mapOf("x" to "HELLO")))
    }

    @Test
    fun `truncate filter truncates long text`() {
        assertEquals(
            "Hello...",
            engine.render("{{ x | truncate(5) }}", mapOf("x" to "Hello, World!")),
        )
    }

    @Test
    fun `truncate filter does not truncate short text`() {
        assertEquals(
            "Hi",
            engine.render("{{ x | truncate(10) }}", mapOf("x" to "Hi")),
        )
    }

    @Test
    fun `default filter provides fallback for null`() {
        assertEquals(
            "fallback",
            engine.render("""{{ x | default("fallback") }}""", emptyMap()),
        )
    }

    @Test
    fun `default filter provides fallback for empty string`() {
        assertEquals(
            "fallback",
            engine.render("""{{ x | default("fallback") }}""", mapOf("x" to "")),
        )
    }

    @Test
    fun `default filter does not activate for non-empty value`() {
        assertEquals(
            "actual",
            engine.render("""{{ x | default("fallback") }}""", mapOf("x" to "actual")),
        )
    }

    @Test
    fun `trim filter strips whitespace`() {
        assertEquals("hello", engine.render("{{ x | trim }}", mapOf("x" to "  hello  ")))
    }

    @Test
    fun `capitalize filter capitalizes first character`() {
        assertEquals("Hello", engine.render("{{ x | capitalize }}", mapOf("x" to "hello")))
    }

    @Test
    fun `chained filters apply in order`() {
        assertEquals(
            "HELLO",
            engine.render("{{ x | trim | upper }}", mapOf("x" to "  hello  ")),
        )
    }

    @Test
    fun `filter on null value returns empty string`() {
        assertEquals("", engine.render("{{ x | upper }}", emptyMap()))
    }

    @Test
    fun `default filter with single quotes`() {
        assertEquals(
            "fallback",
            engine.render("{{ x | default('fallback') }}", emptyMap()),
        )
    }

    // ── Whitespace control ──────────────────────────────────────────

    @Test
    fun `strip left whitespace with tag`() {
        val template = "Hello   \n{%- if true %}World{% endif %}"
        assertEquals("HelloWorld", engine.render(template, emptyMap()))
    }

    @Test
    fun `strip right whitespace with tag`() {
        val template = "{% if true -%}   \nHello{% endif %}"
        assertEquals("Hello", engine.render(template, emptyMap()))
    }

    @Test
    fun `strip both sides of expression`() {
        val template = "A  \n{{- x -}}  \nB"
        assertEquals("AhelloB", engine.render(template, mapOf("x" to "hello")))
    }

    @Test
    fun `strip whitespace with endif`() {
        val template = "{% if true %}Yes{%- endif %} Done"
        assertEquals("Yes Done", engine.render(template, emptyMap()))
    }

    // ── Comments ────────────────────────────────────────────────────

    @Test
    fun `comments are silently dropped`() {
        val result = engine.render(
            "Hello{# this is a comment #}, World!",
            emptyMap(),
        )
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `multiline comment is dropped`() {
        val result = engine.render(
            "A{# multi\nline\ncomment #}B",
            emptyMap(),
        )
        assertEquals("AB", result)
    }

    // ── TemplateContext integration ──────────────────────────────────

    @Test
    fun `render with TemplateContext object`() {
        val context = TemplateContext().apply {
            set("name", "eZansi")
            set("grade", 6)
        }
        val result = engine.render("{{ name }} Grade {{ grade }}", context)
        assertEquals("eZansi Grade 6", result)
    }

    @Test
    fun `render with TemplateContext using setList`() {
        val context = TemplateContext().apply {
            setList("items", listOf(
                mapOf("text" to "A"),
                mapOf("text" to "B"),
            ))
        }
        val result = engine.render(
            "{% for item in items %}{{ item.text }}{% endfor %}",
            context,
        )
        assertEquals("AB", result)
    }

    @Test
    fun `TemplateContext of factory method`() {
        val context = TemplateContext.of("x" to "hello", "y" to "world")
        val result = engine.render("{{ x }} {{ y }}", context)
        assertEquals("hello world", result)
    }

    // ── Error handling ──────────────────────────────────────────────

    @Test
    fun `unclosed expression throws TemplateException`() {
        try {
            engine.render("{{ unclosed", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Unclosed expression"))
        }
    }

    @Test
    fun `unclosed tag throws TemplateException`() {
        try {
            engine.render("{% unclosed", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Unclosed tag"))
        }
    }

    @Test
    fun `unclosed comment throws TemplateException`() {
        try {
            engine.render("{# unclosed", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Unclosed comment"))
        }
    }

    @Test
    fun `missing endif throws TemplateException`() {
        try {
            engine.render("{% if true %}no end", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Expected"))
        }
    }

    @Test
    fun `missing endfor throws TemplateException`() {
        try {
            engine.render("{% for x in list %}no end", mapOf("list" to listOf<String>()))
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Expected"))
        }
    }

    @Test
    fun `unknown filter throws TemplateException`() {
        try {
            engine.render("{{ x | unknown_filter }}", mapOf("x" to "hello"))
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Unknown filter"))
        }
    }

    @Test
    fun `invalid for syntax throws TemplateException`() {
        try {
            engine.render("{% for %}body{% endfor %}", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Invalid for syntax"))
        }
    }

    @Test
    fun `stray endif throws TemplateException`() {
        try {
            engine.render("{% endif %}", emptyMap())
            fail("Expected TemplateException")
        } catch (e: TemplateException) {
            assertTrue(e.message!!.contains("Unexpected tag"))
        }
    }

    // ── Complex / realistic scenarios ───────────────────────────────

    @Test
    fun `realistic system prompt template rendering`() {
        val systemTemplate = """You are eZansi, a friendly maths tutor.
{% if explanation_style == "step-by-step" %}
Break down into numbered steps.
{% elif explanation_style == "simple" %}
Use simple words.
{% endif %}
{% if reading_level == "simple" %}
Use Grade 4-5 vocabulary.
{% endif %}
RULES:
- Use ONLY the content provided."""

        val context = mapOf<String, Any?>(
            "explanation_style" to "step-by-step",
            "reading_level" to "simple",
        )

        val result = engine.render(systemTemplate, context)
        assertTrue(result.contains("Break down into numbered steps."))
        assertTrue(result.contains("Use Grade 4-5 vocabulary."))
        assertTrue(result.contains("Use ONLY the content provided."))
        assertFalse(result.contains("Use simple words."))
    }

    @Test
    fun `realistic user prompt template rendering`() {
        val userTemplate = """CURRICULUM CONTENT:
{% for chunk in chunks %}
---
{{ chunk.title }}:
{{ chunk.body }}
{% endfor %}
---

QUESTION: {{ question }}"""

        val context = mapOf<String, Any?>(
            "question" to "What is 1/2 + 1/4?",
            "chunks" to listOf(
                mapOf(
                    "title" to "Adding Fractions [term1.fractions]",
                    "body" to "To add fractions with different denominators, find the LCD.",
                ),
                mapOf(
                    "title" to "LCD [term1.fractions.lcd]",
                    "body" to "The Lowest Common Denominator is the smallest number both denominators divide into.",
                ),
            ),
        )

        val result = engine.render(userTemplate, context)
        assertTrue(result.contains("Adding Fractions"))
        assertTrue(result.contains("find the LCD"))
        assertTrue(result.contains("What is 1/2 + 1/4?"))
        assertTrue(result.contains("LCD [term1.fractions.lcd]"))
    }

    @Test
    fun `template with all features combined`() {
        val template = """
{# Template with all features #}
Hello {{ name | default("Student") | upper }}!
{% if level == "advanced" %}
Advanced mode enabled.
{% else %}
Basic mode.
{% endif %}
Topics:
{% for topic in topics %}
- {{ topic.name | capitalize }}: {{ topic.desc | truncate(20) }}
{% endfor %}
""".trimStart()

        val context = mapOf<String, Any?>(
            "name" to null,
            "level" to "basic",
            "topics" to listOf(
                mapOf("name" to "fractions", "desc" to "Learn about adding and subtracting fractions"),
                mapOf("name" to "geometry", "desc" to "Shapes and angles"),
            ),
        )

        val result = engine.render(template, context)
        assertTrue(result.contains("STUDENT"))
        assertTrue(result.contains("Basic mode."))
        assertTrue(result.contains("- Fractions: Learn about adding a..."))
        assertTrue(result.contains("- Geometry: Shapes and angles"))
    }
}
