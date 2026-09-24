package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

/**
 * A Kotlin string literal for [value]: a multi-dollar raw string when the value contains `${`, so engine
 * expressions survive verbatim instead of being read as templates.
 */
internal fun kotlinStringLiteral(value: String): CodeBlock = if (value.contains("\${")) {
    CodeBlock.of("\$\$\"\"\"%L\"\"\"", value)
} else {
    CodeBlock.of("%S", value)
}

internal fun kotlinNullableStringLiteral(value: String?): CodeBlock = value?.let { kotlinStringLiteral(it) } ?: CodeBlock.of("null")

/**
 * A constructor call with every argument named on its own line and a trailing comma — the Kotlin style for
 * calls that do not fit one line. Arguments whose value is `null` are left out so defaults apply.
 */
internal fun kotlinNamedCall(type: TypeName, vararg arguments: Pair<String, CodeBlock?>): CodeBlock {
    val call = CodeBlock.builder().add("%T(⇥", type)
    arguments.forEach { (name, value) -> value?.let { call.add("\n%N = %L,", name, it) } }
    return call.add("⇤\n)").build()
}

/**
 * KotlinPoet indents the continuation lines of a property initializer two extra levels; undoing that keeps a
 * multi-line [kotlinNamedCall] aligned with the `val` it initialises, as it is inside a function body.
 */
internal fun kotlinInitializer(call: CodeBlock): CodeBlock = CodeBlock.of("⇤⇤%L⇥⇥", call)
