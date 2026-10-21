package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * How generated C# code is written: string literals, PascalCase identifiers and namespaces, and member names that must
 * not equal their enclosing type's name.
 */
internal object CSharpSyntax {

    private val ESCAPES = mapOf(
        '\\' to "\\\\",
        '"' to "\\\"",
        '\n' to "\\n",
        '\r' to "\\r",
        '\t' to "\\t",
    )

    /**
     * C# only interpolates in `$"..."`, so an engine expression stays verbatim: `${reasonCode}` becomes
     * `"${reasonCode}"`.
     */
    fun stringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { character -> append(escaped(character)) }
        append('"')
    }

    /**
     * e.g. `"Send contract"`, or `null` for a missing value
     */
    fun nullableStringLiteral(value: String?): String {
        if (value == null) return "null"
        return stringLiteral(value)
    }

    /**
     * PascalCase identifier for a BPMN name, derived from the UPPER_SNAKE_CASE form so that the sanitising already done
     * there applies to C# identifiers too: `miravelo.orderBike` becomes `MiraveloOrderBike`, `3rdReminder` becomes
     * `_3RdReminder`.
     *
     * Because the result always starts with a letter or `_`, it can never collide with a C# keyword
     * (those are all lowercase), so no `@` escaping is needed.
     */
    fun pascalCase(name: String): String {
        val words = name.toUpperSnakeCase().split("_").filter { it.isNotEmpty() }
        val joined = words.joinToString("") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }
        val startsWithLetter = joined.firstOrNull()?.isDigit() == false
        if (startsWithLetter) return joined
        return "_$joined"
    }

    /**
     * The name a member declared as [name] ends up with inside [enclosingType].
     * C# rejects a member named like its enclosing type (CS0542): `Messages` inside `Messages` becomes `Messages_`.
     */
    fun disambiguated(name: String, enclosingType: String?): String {
        if (name != enclosingType) return name
        return name + "_"
    }

    /**
     * The .NET namespace of a package path: every segment in PascalCase, `io.miravelo.process` becomes
     * `Io.Miravelo.Process`. The directory the files are written to keeps the package path as given.
     */
    fun namespaceOf(packagePath: String): String {
        val segments = packagePath.split('.')
        return segments.joinToString(".") { segment -> segment.replaceFirstChar { it.uppercaseChar() } }
    }

    private fun escaped(character: Char): String = when {
        character in ESCAPES -> ESCAPES.getValue(character)
        character.isISOControl() -> "\\u%04x".format(character.code)
        else -> character.toString()
    }
}
