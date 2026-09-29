package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode

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
internal fun kotlinNamedCall(type: TypeName, vararg arguments: Pair<String, CodeBlock?>): CodeBlock = namedCall(type, arguments, continuationIndent = "")

/**
 * A `listOf` call with every element on its own line and a trailing comma.
 */
internal fun kotlinListOf(elements: List<CodeBlock>): CodeBlock = if (elements.isEmpty()) {
    CodeBlock.of("emptyList()")
} else {
    CodeBlock.of("listOf(\n⇤⇤⇥%L,⇤\n)⇥⇥", elements.joinToCode(",\n"))
}

/**
 * A [kotlinNamedCall] used as a property initializer. KotlinPoet indents a statement two extra levels from its
 * first line break on; undoing that right after the break keeps the call aligned with the `val` it initialises.
 */
internal fun kotlinNamedInitializer(type: TypeName, vararg arguments: Pair<String, CodeBlock?>): CodeBlock = namedCall(type, arguments, continuationIndent = "⇤⇤")

private fun namedCall(type: TypeName, arguments: Array<out Pair<String, CodeBlock?>>, continuationIndent: String): CodeBlock {
    val restoredIndent = continuationIndent.replace('⇤', '⇥')
    val argumentLines = arguments.mapNotNull { (name, value) -> value?.let { CodeBlock.of("%N = %L,", name, it) } }
    return CodeBlock.builder()
        .add("%T(⇥\n$continuationIndent", type)
        .add(argumentLines.joinToCode("\n"))
        .add("⇤\n)$restoredIndent")
        .build()
}

private val publicModifier = Regex("""(?m)^(\s*)public """)

/**
 * KotlinPoet writes `public` on every declaration; Kotlin's default visibility makes it noise. Only line-leading
 * modifiers are removed so string literals containing "public " stay intact.
 */
internal fun String.withoutPublicModifiers(): String = replace(publicModifier, "$1")
