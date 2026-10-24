package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.domain.ProcessApiNamingException
import javax.lang.model.SourceVersion

/**
 * How values and names are written in generated Java code.
 */
internal object JavaSyntax : JvmSyntax {

    private val ESCAPES = mapOf(
        '\b' to "\\b",
        '\t' to "\\t",
        '\n' to "\\n",
        '\u000c' to "\\f",
        '\r' to "\\r",
        '"' to "\\\"",
        '\\' to "\\\\",
    )

    override fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    /**
     * [name] unchanged.
     * Generation fails on a name that is no valid Java identifier, such as `send-contract`,
     * rather than emitting code that does not compile.
     */
    override fun identifier(name: String): String {
        if (SourceVersion.isName(name)) return name
        throw ProcessApiNamingException("'$name' is not a valid Java name. Rename the element or definition it is generated from.")
    }

    private fun escaped(character: Char): String = when {
        character in ESCAPES -> ESCAPES.getValue(character)
        character.isISOControl() -> "\\u%04x".format(character.code)
        else -> character.toString()
    }
}
