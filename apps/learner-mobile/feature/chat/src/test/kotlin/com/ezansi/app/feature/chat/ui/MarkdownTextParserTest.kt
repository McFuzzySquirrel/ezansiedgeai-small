package com.ezansi.app.feature.chat.ui

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Markdown parsing functions in [MarkdownText].
 *
 * Tests [parseMarkdownBlocks] (block-level parsing) and
 * [convertLatexToReadable] (LaTeX-to-Unicode conversion) — both are
 * internal pure functions that can be tested without Compose UI.
 *
 * The inline formatting (bold, italic, code) requires Compose's
 * AnnotatedString and is tested via the block-level structure.
 */
@DisplayName("Markdown Text Parser")
class MarkdownTextParserTest {

    // ── Block-level parsing ─────────────────────────────────────────

    @Nested
    @DisplayName("Block-level parsing")
    inner class BlockParsingTests {

        @Test
        @DisplayName("plain text becomes a Paragraph")
        fun plainTextParagraph() {
            val blocks = parseMarkdownBlocks("Hello world")
            assertEquals(1, blocks.size)
            assertIs<MarkdownBlock.Paragraph>(blocks[0])
            assertEquals("Hello world", (blocks[0] as MarkdownBlock.Paragraph).text)
        }

        @Test
        @DisplayName("multiple plain lines merge into one Paragraph")
        fun multipleLinesOneParagraph() {
            val blocks = parseMarkdownBlocks("Line one\nLine two\nLine three")
            assertEquals(1, blocks.size)
            assertIs<MarkdownBlock.Paragraph>(blocks[0])
            assertEquals(
                "Line one Line two Line three",
                (blocks[0] as MarkdownBlock.Paragraph).text,
            )
        }

        @Test
        @DisplayName("empty lines between paragraphs split them")
        fun emptyLinesSplitParagraphs() {
            val blocks = parseMarkdownBlocks("First paragraph\n\nSecond paragraph")
            assertEquals(2, blocks.size)
            assertIs<MarkdownBlock.Paragraph>(blocks[0])
            assertIs<MarkdownBlock.Paragraph>(blocks[1])
        }

        @Test
        @DisplayName("empty input produces empty list")
        fun emptyInput() {
            val blocks = parseMarkdownBlocks("")
            assertTrue(blocks.isEmpty())
        }
    }

    // ── Headings ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Headings")
    inner class HeadingTests {

        @Test
        @DisplayName("# H1 heading")
        fun h1Heading() {
            val blocks = parseMarkdownBlocks("# My Heading")
            assertEquals(1, blocks.size)
            val heading = blocks[0] as MarkdownBlock.Heading
            assertEquals(1, heading.level)
            assertEquals("My Heading", heading.text)
        }

        @Test
        @DisplayName("## H2 heading")
        fun h2Heading() {
            val blocks = parseMarkdownBlocks("## Sub Heading")
            val heading = blocks[0] as MarkdownBlock.Heading
            assertEquals(2, heading.level)
            assertEquals("Sub Heading", heading.text)
        }

        @Test
        @DisplayName("### H3 heading")
        fun h3Heading() {
            val blocks = parseMarkdownBlocks("### Small Heading")
            val heading = blocks[0] as MarkdownBlock.Heading
            assertEquals(3, heading.level)
            assertEquals("Small Heading", heading.text)
        }

        @Test
        @DisplayName("heading level capped at 3")
        fun headingLevelCappedAt3() {
            val blocks = parseMarkdownBlocks("#### This is still level 3")
            val heading = blocks[0] as MarkdownBlock.Heading
            assertEquals(3, heading.level)
        }
    }

    // ── Code blocks ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Code blocks")
    inner class CodeBlockTests {

        @Test
        @DisplayName("fenced code block with backticks")
        fun fencedCodeBlock() {
            val markdown = "```\nval x = 42\nprintln(x)\n```"
            val blocks = parseMarkdownBlocks(markdown)

            assertEquals(1, blocks.size)
            val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
            assertEquals("val x = 42\nprintln(x)", codeBlock.code)
        }

        @Test
        @DisplayName("fenced code block with language tag")
        fun fencedCodeBlockWithLanguage() {
            val markdown = "```kotlin\nfun main() { }\n```"
            val blocks = parseMarkdownBlocks(markdown)

            assertEquals(1, blocks.size)
            assertIs<MarkdownBlock.CodeBlock>(blocks[0])
        }

        @Test
        @DisplayName("code block preserves internal lines")
        fun codeBlockPreservesLines() {
            val markdown = "```\nline1\nline2\nline3\n```"
            val blocks = parseMarkdownBlocks(markdown)
            val code = (blocks[0] as MarkdownBlock.CodeBlock).code

            assertEquals("line1\nline2\nline3", code)
        }
    }

    // ── Lists ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lists")
    inner class ListTests {

        @Test
        @DisplayName("bullet list with dash prefix")
        fun bulletListDash() {
            val blocks = parseMarkdownBlocks("- First item\n- Second item")
            assertEquals(2, blocks.size)
            val item1 = blocks[0] as MarkdownBlock.BulletItem
            val item2 = blocks[1] as MarkdownBlock.BulletItem
            assertEquals("First item", item1.text)
            assertEquals("Second item", item2.text)
        }

        @Test
        @DisplayName("bullet list with asterisk prefix")
        fun bulletListAsterisk() {
            val blocks = parseMarkdownBlocks("* Apple\n* Banana")
            assertEquals(2, blocks.size)
            assertIs<MarkdownBlock.BulletItem>(blocks[0])
            assertIs<MarkdownBlock.BulletItem>(blocks[1])
        }

        @Test
        @DisplayName("numbered list")
        fun numberedList() {
            val markdown = "1. First step\n2. Second step\n3. Third step"
            val blocks = parseMarkdownBlocks(markdown)

            assertEquals(3, blocks.size)
            val item1 = blocks[0] as MarkdownBlock.NumberedItem
            assertEquals(1, item1.number)
            assertEquals("First step", item1.text)

            val item3 = blocks[2] as MarkdownBlock.NumberedItem
            assertEquals(3, item3.number)
            assertEquals("Third step", item3.text)
        }
    }

    // ── Mixed content ───────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed content")
    inner class MixedContentTests {

        @Test
        @DisplayName("heading followed by paragraph and list")
        fun headingParagraphList() {
            val markdown = """
                # Fractions
                Here's how to add fractions:
                1. Find the common denominator
                2. Add the numerators
            """.trimIndent()

            val blocks = parseMarkdownBlocks(markdown)
            assertEquals(3, blocks.size)
            assertIs<MarkdownBlock.Heading>(blocks[0])
            assertIs<MarkdownBlock.Paragraph>(blocks[1])
            assertIs<MarkdownBlock.NumberedItem>(blocks[2])
        }

        @Test
        @DisplayName("paragraph then code block then paragraph")
        fun paragraphCodeParagraph() {
            val markdown = "Some text\n\n```\ncode here\n```\n\nMore text"
            val blocks = parseMarkdownBlocks(markdown)

            assertEquals(3, blocks.size)
            assertIs<MarkdownBlock.Paragraph>(blocks[0])
            assertIs<MarkdownBlock.CodeBlock>(blocks[1])
            assertIs<MarkdownBlock.Paragraph>(blocks[2])
        }
    }

    // ── LaTeX conversion ────────────────────────────────────────────

    @Nested
    @DisplayName("LaTeX to readable text conversion")
    inner class LatexConversionTests {

        @Test
        @DisplayName("\\frac{1}{2} → 1/2")
        fun fractionConversion() {
            assertEquals("1/2", convertLatexToReadable("\\frac{1}{2}"))
        }

        @Test
        @DisplayName("\\frac{a}{b} → a/b")
        fun variableFractionConversion() {
            assertEquals("a/b", convertLatexToReadable("\\frac{a}{b}"))
        }

        @Test
        @DisplayName("\\sqrt{9} → √9")
        fun squareRootConversion() {
            assertEquals("√9", convertLatexToReadable("\\sqrt{9}"))
        }

        @Test
        @DisplayName("\\times → ×")
        fun timesSymbol() {
            assertEquals("3 × 4", convertLatexToReadable("3 \\times 4"))
        }

        @Test
        @DisplayName("\\div → ÷")
        fun divisionSymbol() {
            assertEquals("12 ÷ 3", convertLatexToReadable("12 \\div 3"))
        }

        @Test
        @DisplayName("\\leq and \\geq → ≤ and ≥")
        fun comparisonSymbols() {
            assertEquals("x ≤ 5", convertLatexToReadable("x \\leq 5"))
            assertEquals("y ≥ 10", convertLatexToReadable("y \\geq 10"))
        }

        @Test
        @DisplayName("\\pi → π")
        fun piSymbol() {
            assertEquals("π", convertLatexToReadable("\\pi"))
        }

        @Test
        @DisplayName("\\neq → ≠")
        fun notEqualSymbol() {
            assertEquals("x ≠ y", convertLatexToReadable("x \\neq y"))
        }

        @Test
        @DisplayName("superscript ^{2} → ²")
        fun superscriptBraced() {
            assertEquals("x²", convertLatexToReadable("x^{2}"))
        }

        @Test
        @DisplayName("superscript ^3 → ³")
        fun superscriptDigit() {
            assertEquals("x³", convertLatexToReadable("x^3"))
        }

        @Test
        @DisplayName("inline math delimiters $...$ are stripped")
        fun inlineMathDelimiters() {
            assertEquals("x + y", convertLatexToReadable("\$x + y\$"))
        }

        @Test
        @DisplayName("complex expression converts correctly")
        fun complexExpression() {
            val input = "\\frac{1}{2} + \\frac{1}{4} = \\frac{3}{4}"
            val expected = "1/2 + 1/4 = 3/4"
            assertEquals(expected, convertLatexToReadable(input))
        }

        @Test
        @DisplayName("plain text passes through unchanged")
        fun plainTextUnchanged() {
            assertEquals("Hello world", convertLatexToReadable("Hello world"))
        }

        @Test
        @DisplayName("remaining LaTeX commands are stripped")
        fun remainingCommandsStripped() {
            assertEquals("hello", convertLatexToReadable("\\text{hello}"))
        }

        @Test
        @DisplayName("\\pm → ±")
        fun plusMinusSymbol() {
            assertEquals("±5", convertLatexToReadable("\\pm5"))
        }

        @Test
        @DisplayName("\\approx → ≈")
        fun approxSymbol() {
            assertEquals("3.14 ≈ π", convertLatexToReadable("3.14 \\approx \\pi"))
        }

        @Test
        @DisplayName("subscript _{n} → _n")
        fun subscriptBraced() {
            assertEquals("x_n", convertLatexToReadable("x_{n}"))
        }
    }
}
