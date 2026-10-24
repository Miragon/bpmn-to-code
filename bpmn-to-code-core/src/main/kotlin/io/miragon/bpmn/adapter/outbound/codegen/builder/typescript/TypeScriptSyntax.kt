package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.domain.ProcessApiNamingException

/**
 * How values and names are written in generated TypeScript code.
 */
internal object TypeScriptSyntax {

    private val IDENTIFIER = Regex("[\\p{L}_$][\\p{L}\\p{N}_$]*")

    private val ESCAPES = mapOf(
        '\\' to "\\\\",
        '"' to "\\\"",
        '\n' to "\\n",
        '\r' to "\\r",
        '\t' to "\\t",
    )

    /**
     * Double-quoted strings do not interpolate, so an engine expression stays verbatim: `${reasonCode}` becomes
     * `"${reasonCode}"`.
     */
    fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(ESCAPES[character] ?: character.toString()) }
        append('"')
    }

    /**
     * [name] unchanged; generation fails on a name that is no valid identifier, such as `Task.Approve`, rather than
     * emitting code that does not compile.
     */
    fun identifier(name: String): String {
        if (IDENTIFIER.matches(name)) return name
        throw ProcessApiNamingException("'$name' is not a valid TypeScript name. Rename the element or definition it is generated from.")
    }
}
