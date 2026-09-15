package io.miragon.bpmn.adapter.outbound.codegen.writer

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * Accumulates C# source text with block-aware indentation.
 *
 * Kotlin and Java are emitted through KotlinPoet / JavaPoet, which model a type tree and render it.
 * No comparable library for C# is on the classpath, so the C# builder writes text directly and this
 * class is the whole of what it needs: nested blocks, constants, and doc comments.
 */
internal class CSharpWriter {

    private val content = StringBuilder()
    private var indentLevel = 0
    private val enclosingTypes = ArrayDeque<String>()

    fun line(text: String = "") {
        if (text.isEmpty()) {
            content.append('\n')
        } else {
            content.append(INDENT.repeat(indentLevel)).append(text).append('\n')
        }
    }

    private fun block(header: String, body: () -> Unit) {
        line(header)
        line("{")
        indentLevel++
        body()
        indentLevel--
        line("}")
    }

    fun staticClass(name: String, body: () -> Unit) {
        val typeName = disambiguate(name)
        block("public static class $typeName") {
            enclosingTypes.addLast(typeName)
            body()
            enclosingTypes.removeLast()
        }
    }

    fun constant(name: String, value: String) = line("public const string ${disambiguate(name)} = ${stringLiteral(value)};")

    /**
     * C# rejects a member that shares its name with the type enclosing it (CS0542), which a BPMN element
     * called `Elements` or a timer called `Timers` would otherwise produce. The JVM builders never hit this
     * because their constants are UPPER_SNAKE_CASE and so can never equal a PascalCase type name.
     */
    private fun disambiguate(name: String) = if (name == enclosingTypes.lastOrNull()) name + "_" else name

    /**
     * Emits an XML documentation comment. Multi-line text becomes one `<para>` per line so the
     * rendered IntelliSense tooltip keeps the breaks.
     */
    fun docComment(text: String) {
        val lines = text.trim().lines()
        line("/// <summary>")
        lines.forEach { line("/// ${if (lines.size > 1) "<para>${it.escapeXml()}</para>" else it.escapeXml()}") }
        line("/// </summary>")
    }

    fun <T> forEachSeparated(items: List<T>, action: (T) -> Unit) {
        items.forEachIndexed { index, item ->
            if (index > 0) line()
            action(item)
        }
    }

    fun render(): String = content.toString()

    companion object {

        private const val INDENT = "    "

        /**
         * C# only interpolates in `$"..."`, so BPMN expression values such as `${reasonCode}` need no
         * special treatment — unlike Kotlin, where the builder falls back to a raw string.
         */
        private fun stringLiteral(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }

        /**
         * PascalCase identifier for a BPMN name, derived from the UPPER_SNAKE_CASE form so that the
         * sanitising already done there — stripping expression syntax, collapsing `.`/`-`/`:`, guarding a
         * leading digit — applies to C# identifiers too.
         *
         * Because the result always starts with a letter or `_`, it can never collide with a C# keyword
         * (those are all lowercase), so no `@` escaping is needed.
         */
        fun String.toPascalCase(): String = toUpperSnakeCase()
            .split("_")
            .filter { it.isNotEmpty() }
            .joinToString("") { segment -> segment.lowercase().replaceFirstChar { it.uppercaseChar() } }
            .let { if (it.firstOrNull()?.isDigit() != false) "_$it" else it }

        private fun String.escapeXml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
