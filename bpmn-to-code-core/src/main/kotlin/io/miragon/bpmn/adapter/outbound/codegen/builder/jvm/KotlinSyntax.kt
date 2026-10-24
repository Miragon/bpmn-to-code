package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.freemarker.JvmSyntax

/**
 * How values and names are written in generated Kotlin code.
 */
internal object KotlinSyntax : JvmSyntax {

    private val IDENTIFIER = Regex("[\\p{L}_][\\p{L}\\p{N}_]*")

    private val HARD_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
        "when", "while",
    )

    private val ESCAPES = mapOf(
        '\\' to "\\\\",
        '"' to "\\\"",
        '$' to "\\$",
        '\n' to "\\n",
        '\r' to "\\r",
        '\t' to "\\t",
        '\b' to "\\b",
    )

    /**
     * A plain string literal: `${applicationId}` becomes `"\${applicationId}"`, so the engine expression stays verbatim
     * instead of being read as a string template.
     */
    override fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    /**
     * [name] as it is, or in backticks when it is a keyword or no plain identifier: `in` becomes `` `in` ``.
     */
    override fun identifier(name: String): String {
        val isPlainIdentifier = name !in HARD_KEYWORDS && IDENTIFIER.matches(name)
        if (isPlainIdentifier) return name
        return "`$name`"
    }

    private fun escaped(character: Char): String = when {
        character in ESCAPES -> ESCAPES.getValue(character)
        character.isISOControl() -> "\\u%04x".format(character.code)
        else -> character.toString()
    }
}
