package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

/**
 * How values and names are written in generated TypeScript code.
 */
internal object TypeScriptSyntax {

    private val IDENTIFIER = Regex("[\\p{L}_$][\\p{L}\\p{N}_$]*")

    fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    fun nullableStringLiteral(value: String?): String = value?.let { stringLiteral(it) } ?: "null"

    /**
     * [name] unchanged; generation fails on a name that is no valid identifier rather than emitting code that does not
     * compile.
     */
    fun identifier(name: String): String {
        require(IDENTIFIER.matches(name)) { "not a valid name: $name" }
        return name
    }

    private fun escaped(character: Char): String = when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000c' -> "\\f"
        ' ' -> "\\u2028"
        ' ' -> "\\u2029"
        else -> if (character.isISOControl()) "\\u%04x".format(character.code) else character.toString()
    }
}
