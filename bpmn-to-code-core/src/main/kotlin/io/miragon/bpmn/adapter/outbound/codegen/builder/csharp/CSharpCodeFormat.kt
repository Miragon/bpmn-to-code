package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * How generated C# code is written: string literals, PascalCase identifiers, and member names that must not
 * equal their enclosing type's name.
 */
internal object CSharpCodeFormat {

    /**
     * C# only interpolates in `$"..."`, so BPMN expression values such as `${reasonCode}` need no
     * special treatment — unlike Kotlin, where the builder falls back to a raw string.
     */
    fun stringLiteral(value: String): String = buildString {
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

    fun nullableStringLiteral(value: String?): String = value?.let { stringLiteral(it) } ?: "null"

    /**
     * PascalCase identifier for a BPMN name, derived from the UPPER_SNAKE_CASE form so that the
     * sanitising already done there — stripping expression syntax, collapsing `.`/`-`/`:`, guarding a
     * leading digit — applies to C# identifiers too.
     *
     * Because the result always starts with a letter or `_`, it can never collide with a C# keyword
     * (those are all lowercase), so no `@` escaping is needed.
     */
    fun pascalCase(name: String): String = name.toUpperSnakeCase()
        .split("_")
        .filter { it.isNotEmpty() }
        .joinToString("") { segment -> segment.lowercase().replaceFirstChar { it.uppercaseChar() } }
        .let { if (it.firstOrNull()?.isDigit() != false) "_$it" else it }

    /**
     * The name a member declared as [name] ends up with inside [enclosingType] — for code outside that type
     * that has to reference the member.
     */
    fun disambiguated(name: String, enclosingType: String?): String = if (name == enclosingType) name + "_" else name
}
