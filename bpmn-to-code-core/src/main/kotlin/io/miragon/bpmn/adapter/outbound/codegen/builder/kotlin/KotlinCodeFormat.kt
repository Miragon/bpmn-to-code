package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode

/**
 * How generated Kotlin code is written: string literals, multi-line calls with one argument or element per line and a trailing
 * comma, and declarations without the redundant `public` modifier.
 */
internal object KotlinCodeFormat {

    private val publicModifier = Regex("""(?m)^(\s*)public """)

    /**
     * A string literal for [value]: a multi-dollar raw string when it contains `${`, so engine expressions survive
     * verbatim instead of being read as templates.
     */
    fun stringLiteral(value: String): CodeBlock = if (value.contains($$"${")) {
        CodeBlock.of($$$"$$\"\"\"%L\"\"\"", value)
    } else {
        CodeBlock.of("%S", value)
    }

    fun nullableStringLiteral(value: String?): CodeBlock = value?.let { stringLiteral(it) } ?: CodeBlock.of("null")

    /**
     * Where the generated code is placed. KotlinPoet indents a property initializer two extra levels from its first
     * line break on; [INITIALIZER] undoes that right after the break, so the call stays aligned with its `val`.
     */
    enum class Placement(val continuationIndent: String) {
        EXPRESSION(""),
        INITIALIZER("⇤⇤"),
    }

    /**
     * A constructor call with named arguments. Arguments whose value is `null` are left out so defaults apply.
     */
    fun namedCall(type: TypeName, vararg arguments: Pair<String, CodeBlock?>, placement: Placement = Placement.EXPRESSION): CodeBlock {
        val restoredIndent = placement.continuationIndent.replace('⇤', '⇥')
        return CodeBlock.builder()
            .add("%T(⇥\n${placement.continuationIndent}", type)
            .add(argumentLines(arguments))
            .add("⇤\n)$restoredIndent")
            .build()
    }

    /**
     * The named arguments of [namedCall] alone, for a call whose type and parentheses KotlinPoet writes itself, like a
     * supertype's constructor call.
     */
    fun namedArguments(vararg arguments: Pair<String, CodeBlock?>): CodeBlock = CodeBlock.builder()
        .add("⇥\n")
        .add(argumentLines(arguments))
        .add("⇤\n")
        .build()

    private fun argumentLines(arguments: Array<out Pair<String, CodeBlock?>>): CodeBlock {
        val presentArguments = arguments.filter { (_, value) -> value != null }
        val lines = presentArguments.map { (name, value) -> CodeBlock.of("%N = %L,", name, value) }
        return lines.joinToCode("\n")
    }

    /**
     * A `listOf` property initializer referencing the given names.
     */
    fun listOfNames(names: List<String>): CodeBlock = if (names.isEmpty()) {
        CodeBlock.of("emptyList()")
    } else {
        CodeBlock.of("listOf(\n⇤⇤⇥%L,⇤\n)⇥⇥", names.map { CodeBlock.of("%N", it) }.joinToCode(",\n"))
    }

    /**
     * KotlinPoet writes `public` on every declaration; Kotlin's default visibility makes it noise. Only line-leading
     * modifiers are removed so string literals containing "public " stay intact.
     */
    fun withoutPublicModifiers(source: String): String = source.replace(publicModifier, "$1")
}
