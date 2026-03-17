package com.ezansi.app.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders Markdown-formatted text as native Compose UI elements.
 *
 * Supports a subset of Markdown aligned with the content pack format:
 * - Headings (# ## ###)
 * - Bold (**text**) and italic (*text*)
 * - Inline code (`code`) and fenced code blocks (```)
 * - Bullet lists (- item or * item) and numbered lists (1. item)
 * - LaTeX-lite math notation rendered as readable Unicode text
 *
 * No external library is used — this keeps the APK small and avoids
 * WebView overhead that would violate the no-WebView constraint (§12.3).
 *
 * @param text The raw Markdown text to render.
 * @param modifier Layout modifier.
 * @param color Text colour override; uses [LocalContentColor] if unspecified.
 * @param style Base text style; uses [LocalTextStyle] if unspecified.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val resolvedColor = if (color != Color.Unspecified) color else LocalContentColor.current
    val resolvedStyle = style.copy(color = resolvedColor)

    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    HeadingBlock(block, resolvedColor, codeBackground)
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotatedString(block.text, codeBackground),
                        style = resolvedStyle,
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockView(block.code, resolvedColor)
                }
                is MarkdownBlock.BulletItem -> {
                    BulletItemRow(block.text, resolvedStyle, codeBackground)
                }
                is MarkdownBlock.NumberedItem -> {
                    NumberedItemRow(block.number, block.text, resolvedStyle, codeBackground)
                }
            }
        }
    }
}

// ── Block-level composables ─────────────────────────────────────────

@Composable
private fun HeadingBlock(heading: MarkdownBlock.Heading, color: Color, codeBackground: Color) {
    val headingStyle = when (heading.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = buildInlineAnnotatedString(heading.text, codeBackground),
        style = headingStyle,
        color = color,
        modifier = Modifier.padding(top = if (heading.level == 1) 8.dp else 4.dp),
    )
}

@Composable
private fun CodeBlockView(code: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = color,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun BulletItemRow(text: String, style: TextStyle, codeBackground: Color) {
    Row(modifier = Modifier.padding(start = 8.dp)) {
        Text("\u2022  ", style = style)
        Text(
            text = buildInlineAnnotatedString(text, codeBackground),
            style = style,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberedItemRow(number: Int, text: String, style: TextStyle, codeBackground: Color) {
    Row(modifier = Modifier.padding(start = 8.dp)) {
        Text("$number.  ", style = style)
        Text(
            text = buildInlineAnnotatedString(text, codeBackground),
            style = style,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Block-level parser ──────────────────────────────────────────────

/** Parsed Markdown block types. */
internal sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: Int, val text: String) : MarkdownBlock()
}

/** Regex for numbered list items: "1. text", "2. text", etc. */
private val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.*)")

/**
 * Parses raw Markdown text into a list of block-level elements.
 *
 * Processes line-by-line, detecting code fences, headings, list items,
 * and grouping consecutive plain text into paragraphs.
 */
internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            // Empty line — skip
            trimmed.isEmpty() -> {
                i++
            }

            // Fenced code block: ```language ... ```
            trimmed.startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                i++ // skip opening fence
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n")))
                if (i < lines.size) i++ // skip closing fence
            }

            // Heading: # Text, ## Text, ### Text
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                val headingText = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Heading(level, headingText))
                i++
            }

            // Bullet list item: - text or * text
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.drop(2)))
                i++
            }

            // Numbered list item: 1. text
            NUMBERED_LIST_REGEX.matches(trimmed) -> {
                val match = NUMBERED_LIST_REGEX.matchEntire(trimmed)!!
                val number = match.groupValues[1].toIntOrNull() ?: 1
                val itemText = match.groupValues[2]
                blocks.add(MarkdownBlock.NumberedItem(number, itemText))
                i++
            }

            // Regular paragraph — collect consecutive non-special lines
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isEmpty() ||
                        nextLine.startsWith("#") ||
                        nextLine.startsWith("```") ||
                        nextLine.startsWith("- ") ||
                        nextLine.startsWith("* ") ||
                        NUMBERED_LIST_REGEX.matches(nextLine)
                    ) {
                        break
                    }
                    paragraphLines.add(nextLine)
                    i++
                }
                if (paragraphLines.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
                }
            }
        }
    }

    return blocks
}

// ── Inline formatting ───────────────────────────────────────────────

/**
 * Builds an [AnnotatedString] with inline Markdown formatting.
 *
 * Handles bold (**text**), italic (*text*), inline code (`text`),
 * and LaTeX-lite math notation converted to readable Unicode.
 */
internal fun buildInlineAnnotatedString(text: String, codeBackground: Color): AnnotatedString {
    // Pre-process: convert LaTeX-lite notation to readable text
    val processed = convertLatexToReadable(text)

    return buildAnnotatedString {
        var i = 0
        while (i < processed.length) {
            when {
                // Bold: **text**
                i + 1 < processed.length &&
                    processed[i] == '*' && processed[i + 1] == '*' -> {
                    val end = processed.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(processed.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(processed[i])
                        i++
                    }
                }

                // Inline code: `text`
                processed[i] == '`' -> {
                    val end = processed.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                            ),
                        ) {
                            append(processed.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(processed[i])
                        i++
                    }
                }

                // Italic: *text* (single asterisk, not preceded by another asterisk)
                processed[i] == '*' &&
                    (i == 0 || processed[i - 1] != '*') &&
                    i + 1 < processed.length && processed[i + 1] != '*' -> {
                    val end = findClosingItalic(processed, i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(processed.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(processed[i])
                        i++
                    }
                }

                else -> {
                    append(processed[i])
                    i++
                }
            }
        }
    }
}

/**
 * Finds the closing single asterisk for italic text.
 * Returns -1 if not found or if the asterisk is part of a bold marker.
 */
private fun findClosingItalic(text: String, startFrom: Int): Int {
    var pos = text.indexOf('*', startFrom)
    while (pos != -1) {
        // Make sure this isn't part of ** (bold)
        if (pos + 1 >= text.length || text[pos + 1] != '*') {
            return pos
        }
        pos = text.indexOf('*', pos + 2)
    }
    return -1
}

// ── LaTeX-lite conversion ───────────────────────────────────────────

/** Regex patterns for LaTeX constructs, compiled once. */
private val LATEX_FRAC_REGEX = Regex("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}")
private val LATEX_SQRT_REGEX = Regex("\\\\sqrt\\{([^}]*)\\}")
private val LATEX_SUPERSCRIPT_BRACED_REGEX = Regex("\\^\\{([^}]*)\\}")
private val LATEX_SUPERSCRIPT_DIGIT_REGEX = Regex("\\^(\\d)")
private val LATEX_SUBSCRIPT_BRACED_REGEX = Regex("_\\{([^}]*)\\}")
private val LATEX_INLINE_MATH_REGEX = Regex("\\$([^$]+)\\$")
private val LATEX_REMAINING_COMMANDS_REGEX = Regex("\\\\[a-zA-Z]+")

/** Unicode superscript digits for concise mathematical rendering. */
private val SUPERSCRIPT_DIGITS = mapOf(
    '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3',
    '4' to '\u2074', '5' to '\u2075', '6' to '\u2076', '7' to '\u2077',
    '8' to '\u2078', '9' to '\u2079',
)

/**
 * Converts LaTeX-lite mathematical notation to readable Unicode text.
 *
 * Handles common constructs used in Grade 6 maths content:
 * - `\frac{a}{b}` → `a/b`
 * - `\sqrt{x}` → `√x`
 * - Common symbols: `\times` → `×`, `\div` → `÷`, `\pi` → `π`, etc.
 * - Superscripts: `^{2}` → `²`, `^{n}` → `^n`
 * - Inline math delimiters: `$...$` → content without dollar signs
 */
internal fun convertLatexToReadable(text: String): String {
    var result = text

    // Strip inline math delimiters: $x + y$ → x + y
    result = LATEX_INLINE_MATH_REGEX.replace(result) { it.groupValues[1] }

    // Fractions: \frac{a}{b} → a/b
    result = LATEX_FRAC_REGEX.replace(result) { "${it.groupValues[1]}/${it.groupValues[2]}" }

    // Square root: \sqrt{x} → √x
    result = LATEX_SQRT_REGEX.replace(result) { "\u221A${it.groupValues[1]}" }

    // Common LaTeX symbols → Unicode equivalents
    result = result
        .replace("\\times", "\u00D7")
        .replace("\\div", "\u00F7")
        .replace("\\leq", "\u2264")
        .replace("\\geq", "\u2265")
        .replace("\\neq", "\u2260")
        .replace("\\approx", "\u2248")
        .replace("\\pi", "\u03C0")
        .replace("\\sum", "\u03A3")
        .replace("\\infty", "\u221E")
        .replace("\\pm", "\u00B1")
        .replace("\\cdot", "\u00B7")
        .replace("\\ldots", "\u2026")

    // Superscripts with braces: ^{2} → ²
    result = LATEX_SUPERSCRIPT_BRACED_REGEX.replace(result) { match ->
        val content = match.groupValues[1]
        // Try to convert each character to its superscript Unicode equivalent
        val superscripted = content.map { ch -> SUPERSCRIPT_DIGITS[ch] ?: ch }.joinToString("")
        if (superscripted != content) superscripted else "^$content"
    }

    // Superscript single digit: ^2 → ²
    result = LATEX_SUPERSCRIPT_DIGIT_REGEX.replace(result) { match ->
        val digit = match.groupValues[1][0]
        (SUPERSCRIPT_DIGITS[digit] ?: digit).toString()
    }

    // Subscripts with braces: _{n} → _n (no good Unicode for arbitrary subscripts)
    result = LATEX_SUBSCRIPT_BRACED_REGEX.replace(result) { "_${it.groupValues[1]}" }

    // Clean up any remaining LaTeX commands (e.g. \text{...})
    result = LATEX_REMAINING_COMMANDS_REGEX.replace(result, "")

    return result
}
