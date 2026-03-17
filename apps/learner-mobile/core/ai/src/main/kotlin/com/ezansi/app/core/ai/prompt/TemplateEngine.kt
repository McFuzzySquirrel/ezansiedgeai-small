package com.ezansi.app.core.ai.prompt

/**
 * Lightweight Jinja2-compatible template engine for on-device prompt construction.
 *
 * Implements a safe subset of the Jinja2 template language using a recursive-descent
 * parser. The engine supports variable substitution, conditionals, loops, and filters
 * — everything needed for data-driven prompt templates — without any `eval()`,
 * reflection, or dynamic code execution.
 *
 * ## Supported Syntax
 *
 * | Feature | Syntax | Example |
 * |---------|--------|---------|
 * | Variables | `{{ variable }}` | `{{ question }}` |
 * | Dot access | `{{ obj.field }}` | `{{ chunk.title }}` |
 * | Conditionals | `{% if %}...{% elif %}...{% else %}...{% endif %}` | See below |
 * | Loops | `{% for item in list %}...{% endfor %}` | See below |
 * | Filters | `{{ value \| filter }}` | `{{ text \| upper }}` |
 * | Whitespace | `{%- ... -%}` | Strips surrounding whitespace |
 * | Comments | `{# comment #}` | Ignored in output |
 *
 * ## Supported Filters
 *
 * - `upper` — Converts to uppercase
 * - `lower` — Converts to lowercase
 * - `truncate(N)` — Truncates to N characters with "..." suffix
 * - `default("fallback")` — Uses fallback when value is null/empty
 * - `trim` — Strips leading/trailing whitespace
 * - `capitalize` — Capitalizes first character
 *
 * ## Condition Operators
 *
 * - Truthiness: `{% if variable %}` — checks non-null, non-empty, non-false
 * - Equality: `{% if variable == "value" %}`
 * - Inequality: `{% if variable != "value" %}`
 * - Negation: `{% if not variable %}`
 *
 * ## Safety
 *
 * This engine is deliberately limited. It cannot execute arbitrary code, access
 * the filesystem, or perform reflection. All data must be explicitly provided
 * via the context map. This is critical for a learner-facing educational app
 * where prompt templates may come from content packs.
 *
 * @see TemplateContext for building the rendering context
 * @see com.ezansi.app.core.ai.prompt.templates.DefaultTemplates for bundled templates
 */
class TemplateEngine {

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Renders a template string with values from a [TemplateContext].
     *
     * @param template The Jinja2-style template string.
     * @param context The context containing variable values.
     * @return The rendered output string.
     * @throws TemplateException if the template has syntax errors.
     */
    fun render(template: String, context: TemplateContext): String =
        render(template, context.toMap())

    /**
     * Renders a template string with values from a plain map.
     *
     * This is the core render method. The template is tokenized, parsed
     * into an AST, and then rendered by walking the tree with the context.
     *
     * @param template The Jinja2-style template string.
     * @param context Map of variable names to values.
     * @return The rendered output string.
     * @throws TemplateException if the template has syntax errors.
     */
    fun render(template: String, context: Map<String, Any?>): String {
        if (template.isEmpty()) return ""

        val tokens = tokenize(template)
        val processed = applyWhitespaceControl(tokens)
        val ast = parse(processed)
        return renderNodes(ast, context.toMutableMap())
    }

    // ── Token types ─────────────────────────────────────────────────

    /**
     * Tokens produced by the lexer. Each token represents a segment of the
     * template: literal text, an expression `{{ }}`, or a control tag `{% %}`.
     */
    internal sealed class Token {
        /** Raw text between template tags — rendered verbatim. */
        data class Text(val content: String) : Token()

        /** Expression block `{{ ... }}` — evaluated and interpolated. */
        data class Expression(
            val content: String,
            val stripLeft: Boolean = false,
            val stripRight: Boolean = false,
        ) : Token()

        /** Control tag `{% ... %}` — if/elif/else/endif/for/endfor. */
        data class Tag(
            val content: String,
            val stripLeft: Boolean = false,
            val stripRight: Boolean = false,
        ) : Token()
    }

    // ── AST node types ──────────────────────────────────────────────

    /** Abstract syntax tree nodes produced by the parser. */
    internal sealed class Node {
        /** Literal text — rendered as-is. */
        data class TextNode(val text: String) : Node()

        /** Expression — variable lookup with optional filters. */
        data class ExpressionNode(val expression: String) : Node()

        /** Conditional block with one or more branches and an optional else. */
        data class IfNode(
            val branches: List<ConditionalBranch>,
            val elseBranch: List<Node>?,
        ) : Node()

        /** Loop block — iterates over a list, rendering body for each item. */
        data class ForNode(
            val loopVariable: String,
            val iterableExpression: String,
            val body: List<Node>,
        ) : Node()
    }

    /** A single branch of an if/elif conditional chain. */
    internal data class ConditionalBranch(
        val condition: String,
        val body: List<Node>,
    )

    // ── Filter types ────────────────────────────────────────────────

    /** Supported output filters for expression values. */
    private sealed class Filter {
        data object Upper : Filter()
        data object Lower : Filter()
        data object Trim : Filter()
        data object Capitalize : Filter()
        data class Truncate(val maxLength: Int) : Filter()
        data class Default(val fallbackValue: String) : Filter()
    }

    /** Parsed expression: a variable path and zero or more filters. */
    private data class ParsedExpression(
        val variablePath: String,
        val filters: List<Filter>,
    )

    // ── Tokenizer ───────────────────────────────────────────────────

    /**
     * Scans the template string into a flat list of tokens.
     *
     * Recognises three delimiter types:
     * - `{{ ... }}` → [Token.Expression]
     * - `{% ... %}` → [Token.Tag]
     * - `{# ... #}` → Comment (silently dropped)
     *
     * Everything else becomes [Token.Text]. Whitespace control markers
     * (`-` after opening or before closing delimiter) are detected and
     * stored on the token for later processing.
     */
    internal fun tokenize(template: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0

        while (pos < template.length) {
            // Find the earliest opening delimiter from current position
            val nextExpr = template.indexOf("{{", pos)
            val nextTag = template.indexOf("{%", pos)
            val nextComment = template.indexOf("{#", pos)

            data class Candidate(val position: Int, val type: Char)

            val candidates = mutableListOf<Candidate>()
            if (nextExpr >= 0) candidates.add(Candidate(nextExpr, '{'))
            if (nextTag >= 0) candidates.add(Candidate(nextTag, '%'))
            if (nextComment >= 0) candidates.add(Candidate(nextComment, '#'))

            if (candidates.isEmpty()) {
                // No more delimiters — everything remaining is text
                if (pos < template.length) {
                    tokens.add(Token.Text(template.substring(pos)))
                }
                break
            }

            val earliest = candidates.minByOrNull { it.position }!!

            // Capture any literal text before this delimiter
            if (earliest.position > pos) {
                tokens.add(Token.Text(template.substring(pos, earliest.position)))
            }

            when (earliest.type) {
                '{' -> {
                    // Expression: {{ ... }}
                    val afterOpen = earliest.position + 2
                    val stripLeft = template.getOrNull(afterOpen) == '-'
                    val contentStart = if (stripLeft) afterOpen + 1 else afterOpen

                    val closePos = template.indexOf("}}", contentStart)
                    if (closePos < 0) {
                        throw TemplateException(
                            "Unclosed expression '{{' at position ${earliest.position}",
                        )
                    }

                    val stripRight = closePos > contentStart && template[closePos - 1] == '-'
                    val contentEnd = if (stripRight) closePos - 1 else closePos

                    val content = template.substring(contentStart, contentEnd).trim()
                    tokens.add(Token.Expression(content, stripLeft, stripRight))
                    pos = closePos + 2
                }
                '%' -> {
                    // Control tag: {% ... %}
                    val afterOpen = earliest.position + 2
                    val stripLeft = template.getOrNull(afterOpen) == '-'
                    val contentStart = if (stripLeft) afterOpen + 1 else afterOpen

                    val closePos = template.indexOf("%}", contentStart)
                    if (closePos < 0) {
                        throw TemplateException(
                            "Unclosed tag '{%' at position ${earliest.position}",
                        )
                    }

                    val stripRight = closePos > contentStart && template[closePos - 1] == '-'
                    val contentEnd = if (stripRight) closePos - 1 else closePos

                    val content = template.substring(contentStart, contentEnd).trim()
                    tokens.add(Token.Tag(content, stripLeft, stripRight))
                    pos = closePos + 2
                }
                '#' -> {
                    // Comment: {# ... #} — silently dropped
                    val closePos = template.indexOf("#}", earliest.position + 2)
                    if (closePos < 0) {
                        throw TemplateException(
                            "Unclosed comment '{#' at position ${earliest.position}",
                        )
                    }
                    pos = closePos + 2
                }
            }
        }

        return tokens
    }

    // ── Whitespace control ──────────────────────────────────────────

    /**
     * Applies Jinja2-style whitespace stripping to adjacent text tokens.
     *
     * When a delimiter uses `{%-` or `{{-`, whitespace (including newlines)
     * is stripped from the END of the preceding text token. When `-%}` or
     * `-}}` is used, whitespace is stripped from the START of the following
     * text token.
     */
    private fun applyWhitespaceControl(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens

        val result = tokens.toMutableList()

        for (i in result.indices) {
            val token = result[i]

            val stripLeft = when (token) {
                is Token.Expression -> token.stripLeft
                is Token.Tag -> token.stripLeft
                else -> false
            }
            val stripRight = when (token) {
                is Token.Expression -> token.stripRight
                is Token.Tag -> token.stripRight
                else -> false
            }

            // Strip trailing whitespace from preceding text
            if (stripLeft && i > 0) {
                val prev = result[i - 1]
                if (prev is Token.Text) {
                    result[i - 1] = Token.Text(prev.content.trimEnd())
                }
            }

            // Strip leading whitespace from following text
            if (stripRight && i + 1 < result.size) {
                val next = result[i + 1]
                if (next is Token.Text) {
                    result[i + 1] = Token.Text(next.content.trimStart())
                }
            }
        }

        // Remove empty text tokens produced by stripping
        return result.filter { it !is Token.Text || it.content.isNotEmpty() }
    }

    // ── Parser ──────────────────────────────────────────────────────

    /**
     * Parses a flat token list into a hierarchical AST.
     *
     * Uses recursive descent to handle nested structures:
     * if blocks can contain for blocks, for blocks can contain if blocks, etc.
     */
    internal fun parse(tokens: List<Token>): List<Node> {
        val result = parseBody(tokens, 0, endTags = emptySet())
        if (result.endPosition != tokens.size) {
            val remaining = tokens[result.endPosition]
            val tagContent = if (remaining is Token.Tag) remaining.content else "unknown"
            throw TemplateException("Unexpected tag '$tagContent' — not inside a matching block")
        }
        return result.nodes
    }

    /**
     * Result of parsing a block body: the AST nodes, the position of the
     * terminating tag, and the keyword of that tag.
     */
    private data class BodyParseResult(
        val nodes: List<Node>,
        val endPosition: Int,
        val endTagKeyword: String,
    )

    /**
     * Parses nodes from the token list until a tag matching one of [endTags]
     * is encountered, or the end of the list is reached.
     *
     * @param tokens The full token list.
     * @param startPos Position to start parsing from.
     * @param endTags Set of tag keywords that terminate this body (e.g. "endif", "else").
     * @return The parsed nodes, the position of the terminating tag, and its keyword.
     */
    private fun parseBody(
        tokens: List<Token>,
        startPos: Int,
        endTags: Set<String>,
    ): BodyParseResult {
        val nodes = mutableListOf<Node>()
        var pos = startPos

        while (pos < tokens.size) {
            val token = tokens[pos]

            // Check if this tag terminates the current body
            if (token is Token.Tag) {
                val keyword = extractTagKeyword(token.content)
                if (keyword in endTags) {
                    return BodyParseResult(nodes, pos, keyword)
                }
            }

            val (node, nextPos) = parseSingleNode(tokens, pos)
            if (node != null) nodes.add(node)
            pos = nextPos
        }

        if (endTags.isNotEmpty()) {
            throw TemplateException(
                "Expected one of $endTags but reached end of template",
            )
        }

        return BodyParseResult(nodes, pos, "")
    }

    /**
     * Parses a single node starting at [pos], returning the node and the
     * next position to continue parsing from.
     */
    private fun parseSingleNode(tokens: List<Token>, pos: Int): Pair<Node?, Int> {
        return when (val token = tokens[pos]) {
            is Token.Text -> Pair(Node.TextNode(token.content), pos + 1)
            is Token.Expression -> Pair(Node.ExpressionNode(token.content), pos + 1)
            is Token.Tag -> parseTagBlock(tokens, pos)
        }
    }

    /**
     * Parses a control tag and its associated block (if, for).
     */
    private fun parseTagBlock(tokens: List<Token>, pos: Int): Pair<Node?, Int> {
        val tag = tokens[pos] as Token.Tag
        val keyword = extractTagKeyword(tag.content)

        return when (keyword) {
            "if" -> parseIfBlock(tokens, pos)
            "for" -> parseForBlock(tokens, pos)
            else -> throw TemplateException("Unknown tag keyword: '$keyword'")
        }
    }

    /**
     * Parses an if/elif/else/endif block.
     *
     * Structure:
     * ```
     * {% if condition %}
     *   body
     * {% elif condition %}
     *   body
     * {% else %}
     *   body
     * {% endif %}
     * ```
     */
    private fun parseIfBlock(tokens: List<Token>, startPos: Int): Pair<Node, Int> {
        val branches = mutableListOf<ConditionalBranch>()
        var elseBranch: List<Node>? = null

        // Extract the first "if" condition
        val ifTag = (tokens[startPos] as Token.Tag).content.trim()
        val ifCondition = ifTag.removePrefix("if").trim()
        var pos = startPos + 1

        // Parse body until elif, else, or endif
        var bodyResult = parseBody(tokens, pos, setOf("elif", "else", "endif"))
        branches.add(ConditionalBranch(ifCondition, bodyResult.nodes))
        pos = bodyResult.endPosition

        // Handle subsequent elif branches
        while (bodyResult.endTagKeyword == "elif") {
            val elifTag = (tokens[pos] as Token.Tag).content.trim()
            val elifCondition = elifTag.removePrefix("elif").trim()
            pos++

            bodyResult = parseBody(tokens, pos, setOf("elif", "else", "endif"))
            branches.add(ConditionalBranch(elifCondition, bodyResult.nodes))
            pos = bodyResult.endPosition
        }

        // Handle else branch
        if (bodyResult.endTagKeyword == "else") {
            pos++ // skip the "else" tag
            val elseResult = parseBody(tokens, pos, setOf("endif"))
            elseBranch = elseResult.nodes
            pos = elseResult.endPosition
        }

        // Skip the "endif" tag
        pos++

        return Pair(Node.IfNode(branches, elseBranch), pos)
    }

    /**
     * Parses a for/endfor loop block.
     *
     * Structure: `{% for item in iterable %}...{% endfor %}`
     */
    private fun parseForBlock(tokens: List<Token>, startPos: Int): Pair<Node, Int> {
        val forTag = (tokens[startPos] as Token.Tag).content.trim()

        val match = FOR_PATTERN.matchEntire(forTag)
            ?: throw TemplateException("Invalid for syntax: '$forTag'. Expected: for <var> in <list>")

        val loopVariable = match.groupValues[1]
        val iterableExpression = match.groupValues[2].trim()

        val bodyResult = parseBody(tokens, startPos + 1, setOf("endfor"))
        val endPos = bodyResult.endPosition + 1 // skip "endfor" tag

        return Pair(Node.ForNode(loopVariable, iterableExpression, bodyResult.nodes), endPos)
    }

    /**
     * Extracts the keyword from a tag's content (first whitespace-delimited word).
     */
    private fun extractTagKeyword(tagContent: String): String {
        return tagContent.trim().split(WHITESPACE_PATTERN, limit = 2)[0]
    }

    // ── Renderer ────────────────────────────────────────────────────

    /**
     * Renders a list of AST nodes into a string using the given context.
     *
     * The context is mutable to support loop variable injection — each
     * `{% for %}` block temporarily adds the loop variable to the context
     * and removes it when the loop completes.
     */
    private fun renderNodes(nodes: List<Node>, context: MutableMap<String, Any?>): String {
        val builder = StringBuilder()
        for (node in nodes) {
            builder.append(renderNode(node, context))
        }
        return builder.toString()
    }

    /** Renders a single AST node. */
    private fun renderNode(node: Node, context: MutableMap<String, Any?>): String {
        return when (node) {
            is Node.TextNode -> node.text
            is Node.ExpressionNode -> evaluateExpression(node.expression, context)
            is Node.IfNode -> renderIfNode(node, context)
            is Node.ForNode -> renderForNode(node, context)
        }
    }

    /**
     * Renders an if/elif/else block by evaluating branches in order.
     *
     * The first branch whose condition is truthy wins. If no branch
     * matches and there is an else branch, that is rendered instead.
     */
    private fun renderIfNode(node: Node.IfNode, context: MutableMap<String, Any?>): String {
        for (branch in node.branches) {
            if (evaluateCondition(branch.condition, context)) {
                return renderNodes(branch.body, context)
            }
        }
        return if (node.elseBranch != null) {
            renderNodes(node.elseBranch, context)
        } else {
            ""
        }
    }

    /**
     * Renders a for loop by iterating over the resolved list.
     *
     * The loop variable is injected into the context for each iteration
     * and restored to its previous value (or removed) after the loop.
     * This allows nested loops with different variable names.
     *
     * Handles lists of maps (for dot-notation access in the loop body),
     * lists of scalars, and arrays.
     */
    private fun renderForNode(node: Node.ForNode, context: MutableMap<String, Any?>): String {
        val iterable = resolveVariable(node.iterableExpression, context)

        val items: List<Any?> = when (iterable) {
            is List<*> -> iterable
            is Array<*> -> iterable.toList()
            null -> return ""
            else -> return ""
        }

        val builder = StringBuilder()
        val previousValue = context[node.loopVariable]
        val hadPreviousValue = context.containsKey(node.loopVariable)

        for (item in items) {
            context[node.loopVariable] = item
            builder.append(renderNodes(node.body, context))
        }

        // Restore previous context state to avoid leaking loop variables
        if (hadPreviousValue) {
            context[node.loopVariable] = previousValue
        } else {
            context.remove(node.loopVariable)
        }

        return builder.toString()
    }

    // ── Expression evaluation ───────────────────────────────────────

    /**
     * Evaluates an expression string: resolves the variable, applies
     * filters, and returns the string representation.
     *
     * Expression format: `variable.path | filter1 | filter2(arg)`
     */
    private fun evaluateExpression(expression: String, context: Map<String, Any?>): String {
        val parsed = parseExpressionParts(expression)
        var value = resolveVariable(parsed.variablePath, context)

        for (filter in parsed.filters) {
            value = applyFilter(filter, value)
        }

        return value?.toString() ?: ""
    }

    /**
     * Splits an expression into the variable path and filter chain.
     *
     * Handles: `variable`, `variable | filter`, `variable | filter(arg)`,
     * and `variable | filter1 | filter2(arg)`.
     */
    private fun parseExpressionParts(expression: String): ParsedExpression {
        val segments = expression.split("|").map { it.trim() }
        val variablePath = segments[0]
        val filters = segments.drop(1).map { parseFilter(it) }
        return ParsedExpression(variablePath, filters)
    }

    /**
     * Parses a single filter specification into a [Filter] object.
     *
     * @param filterText Text like "upper", "truncate(100)", or "default(\"fallback\")".
     * @throws TemplateException if the filter name is unknown.
     */
    private fun parseFilter(filterText: String): Filter {
        val name = filterText.substringBefore("(").trim()
        val hasArgs = filterText.contains("(") && filterText.contains(")")

        return when (name) {
            "upper" -> Filter.Upper
            "lower" -> Filter.Lower
            "trim" -> Filter.Trim
            "capitalize" -> Filter.Capitalize
            "truncate" -> {
                if (!hasArgs) throw TemplateException("Filter 'truncate' requires an argument: truncate(N)")
                val arg = filterText.substringAfter("(").substringBefore(")").trim()
                val length = arg.toIntOrNull()
                    ?: throw TemplateException("Filter 'truncate' requires an integer argument, got: '$arg'")
                Filter.Truncate(length)
            }
            "default" -> {
                if (!hasArgs) throw TemplateException("Filter 'default' requires an argument: default(\"value\")")
                val arg = filterText.substringAfter("(").substringBefore(")").trim()
                val value = unquoteStringLiteral(arg)
                Filter.Default(value)
            }
            else -> throw TemplateException("Unknown filter: '$name'")
        }
    }

    /**
     * Resolves a dot-separated variable path against the context.
     *
     * Supports nested access: `chunk.title` resolves `context["chunk"]`
     * first, then accesses the "title" key on the resulting map.
     *
     * @return The resolved value, or null if any segment is missing.
     */
    private fun resolveVariable(path: String, context: Map<String, Any?>): Any? {
        val segments = path.trim().split(".")
        var current: Any? = context

        for (segment in segments) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> return null
            }
        }

        return current
    }

    /**
     * Applies a single filter to a value.
     *
     * The `default` filter is special: it activates when the value is
     * null or an empty string. All other filters pass through null values.
     */
    private fun applyFilter(filter: Filter, value: Any?): Any? {
        return when (filter) {
            is Filter.Upper -> value?.toString()?.uppercase()
            is Filter.Lower -> value?.toString()?.lowercase()
            is Filter.Trim -> value?.toString()?.trim()
            is Filter.Capitalize -> value?.toString()?.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
            is Filter.Truncate -> {
                val str = value?.toString() ?: return null
                if (str.length > filter.maxLength) {
                    str.take(filter.maxLength) + "..."
                } else {
                    str
                }
            }
            is Filter.Default -> {
                if (value == null || (value is String && value.isEmpty())) {
                    filter.fallbackValue
                } else {
                    value
                }
            }
        }
    }

    // ── Condition evaluation ────────────────────────────────────────

    /**
     * Evaluates a condition expression for truthiness.
     *
     * Supports:
     * - Simple truthiness: `variable` — true if non-null, non-empty, non-false
     * - Equality: `variable == "value"` — compares string representations
     * - Inequality: `variable != "value"` — inverse of equality
     * - Negation: `not variable` — inverts truthiness
     */
    internal fun evaluateCondition(condition: String, context: Map<String, Any?>): Boolean {
        val trimmed = condition.trim()

        // Handle "not" prefix — must have a space after "not" to avoid matching
        // variable names like "notification" or "not_empty"
        if (trimmed.startsWith("not ")) {
            return !evaluateCondition(trimmed.removePrefix("not ").trim(), context)
        }

        // Handle equality: variable == "value" or variable == 'value'
        val eqIndex = trimmed.indexOf("==")
        if (eqIndex > 0) {
            val leftPath = trimmed.substring(0, eqIndex).trim()
            val rightLiteral = trimmed.substring(eqIndex + 2).trim()
            val leftValue = resolveVariable(leftPath, context)?.toString()
            val rightValue = unquoteStringLiteral(rightLiteral)
            return leftValue == rightValue
        }

        // Handle inequality: variable != "value" or variable != 'value'
        val neqIndex = trimmed.indexOf("!=")
        if (neqIndex > 0) {
            val leftPath = trimmed.substring(0, neqIndex).trim()
            val rightLiteral = trimmed.substring(neqIndex + 2).trim()
            val leftValue = resolveVariable(leftPath, context)?.toString()
            val rightValue = unquoteStringLiteral(rightLiteral)
            return leftValue != rightValue
        }

        // Simple truthiness — resolve the variable and check
        val value = resolveVariable(trimmed, context)
        return isTruthy(value)
    }

    /**
     * Determines if a value is "truthy" using Jinja2-like semantics.
     *
     * Falsy values: null, false, 0, empty string, empty collection.
     * Everything else is truthy.
     */
    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            null -> false
            is Boolean -> value
            is String -> value.isNotEmpty()
            is Number -> value.toDouble() != 0.0
            is Collection<*> -> value.isNotEmpty()
            is Array<*> -> value.isNotEmpty()
            else -> true
        }
    }

    // ── Utilities ───────────────────────────────────────────────────

    /**
     * Strips surrounding quotes (single or double) from a string literal.
     *
     * Returns the content between quotes, or the original string if no
     * quotes are found. Handles both `"value"` and `'value'` styles.
     */
    private fun unquoteStringLiteral(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.length >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'") ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    companion object {
        /** Pattern for `for <variable> in <expression>` syntax. */
        private val FOR_PATTERN = Regex("""for\s+(\w+)\s+in\s+(.+)""")

        /** Whitespace splitting pattern. */
        private val WHITESPACE_PATTERN = Regex("\\s+")
    }
}

/**
 * Exception thrown when a template has invalid syntax or references
 * an unknown filter. Messages are developer-facing, not user-facing.
 */
class TemplateException(message: String) : RuntimeException(message)
