package com.ezansi.app.core.ai.prompt

/**
 * Type-safe context object for template rendering.
 *
 * Holds the data that gets injected into templates during rendering.
 * Supports scalar values, nested maps (for dot-notation access like
 * `{{ chunk.title }}`), and lists of maps (for `{% for %}` loops).
 *
 * ## Usage
 *
 * ```kotlin
 * val context = TemplateContext()
 * context.set("question", "What is 1/2 + 1/4?")
 * context.set("explanation_style", "step-by-step")
 * context.setList("chunks", listOf(
 *     mapOf("title" to "Adding Fractions", "body" to "To add fractions...")
 * ))
 * ```
 *
 * @see TemplateEngine for rendering templates with this context
 */
class TemplateContext {

    private val data = mutableMapOf<String, Any?>()

    /**
     * Sets a scalar value in the context.
     *
     * @param key The variable name used in templates (e.g. "question").
     * @param value The value to inject. Null values render as empty strings.
     */
    fun set(key: String, value: Any?) {
        data[key] = value
    }

    /**
     * Sets a list of maps in the context, used for `{% for %}` loops.
     *
     * Each map in the list becomes the loop variable's value on each
     * iteration, allowing dot-notation access (e.g. `{{ item.title }}`).
     *
     * @param key The list variable name used in `{% for item in key %}`.
     * @param items List of maps, each representing one loop iteration's data.
     */
    fun setList(key: String, items: List<Map<String, Any?>>) {
        data[key] = items
    }

    /**
     * Retrieves a value from the context by key.
     *
     * @return The stored value, or null if the key is not set.
     */
    fun get(key: String): Any? = data[key]

    /**
     * Returns an immutable snapshot of the context as a plain map.
     *
     * Used internally by [TemplateEngine] for rendering. The returned
     * map is a shallow copy — modifying it does not affect this context.
     */
    fun toMap(): Map<String, Any?> = data.toMap()

    companion object {

        /**
         * Creates a context from key-value pairs for concise inline usage.
         *
         * ```kotlin
         * val ctx = TemplateContext.of("name" to "eZansi", "grade" to 6)
         * ```
         */
        fun of(vararg pairs: Pair<String, Any?>): TemplateContext {
            return TemplateContext().apply {
                pairs.forEach { (key, value) -> set(key, value) }
            }
        }
    }
}
