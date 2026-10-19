package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

/**
 * How values and names are written in generated Kotlin code.
 */
internal object KotlinSyntax {

    private val IDENTIFIER = Regex("[\\p{L}_][\\p{L}\\p{N}_]*")

    private val HARD_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
        "when", "while",
    )

    /**
     * A plain string literal. `$` is escaped, so engine expressions such as `${applicationId}` stay verbatim instead of
     * being read as string templates.
     */
    fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    /**
     * [name] as it is, or in backticks when it is a keyword or no plain identifier.
     */
    fun identifier(name: String): String = if (name in HARD_KEYWORDS || !IDENTIFIER.matches(name)) "`$name`" else name

    private fun escaped(character: Char): String = when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '$' -> "\\$"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        else -> if (character.isISOControl()) "\\u%04x".format(character.code) else character.toString()
    }
}
