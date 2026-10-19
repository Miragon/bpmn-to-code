package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import javax.lang.model.SourceVersion

/**
 * How values and names are written in generated Java code.
 */
internal object JavaSyntax {

    fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    fun nullableStringLiteral(value: String?): String = value?.let { stringLiteral(it) } ?: "null"

    /**
     * [name] unchanged; generation fails on a name that is no valid Java identifier rather than emitting code that does
     * not compile.
     */
    fun identifier(name: String): String {
        require(SourceVersion.isName(name)) { "not a valid name: $name" }
        return name
    }

    private fun escaped(character: Char): String = when (character) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\u000c' -> "\\f"
        '\r' -> "\\r"
        '"' -> "\\\""
        '\\' -> "\\\\"
        else -> if (character.isISOControl()) "\\u%04x".format(character.code) else character.toString()
    }
}
