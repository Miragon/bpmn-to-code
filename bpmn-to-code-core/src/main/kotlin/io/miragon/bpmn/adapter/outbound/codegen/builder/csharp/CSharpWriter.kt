package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral

/**
 * Accumulates C# source text with block-aware indentation.
 *
 * Kotlin and Java are emitted through KotlinPoet / JavaPoet, which model a type tree and render it.
 * No comparable library for C# is on the classpath, so the C# builder writes text directly and this
 * class is the whole of what it needs: nested blocks, constants, properties, and doc comments.
 */
internal class CSharpWriter {

    private val content = StringBuilder()
    private var indentLevel = 0
    private val enclosingTypes = ArrayDeque<String>()

    fun line(text: String = "") {
        if (text.isEmpty()) {
            content.append('\n')
        } else {
            content.append(INDENT.repeat(indentLevel)).append(text).append('\n')
        }
    }

    fun staticClass(name: String, body: () -> Unit) = typeBlock(keyword = "public static class", header = disambiguate(name), body = body)

    fun sealedClass(name: String, implements: String? = null, body: () -> Unit) {
        val typeName = disambiguate(name)
        val header = implements?.let { "$typeName : $it" } ?: typeName
        typeBlock(keyword = "public sealed class", header = header, body = body, typeName = typeName)
    }

    private fun typeBlock(keyword: String, header: String, body: () -> Unit, typeName: String = header) {
        line("$keyword $header")
        line("{")
        indentLevel++
        enclosingTypes.addLast(typeName)
        body()
        enclosingTypes.removeLast()
        indentLevel--
        line("}")
    }

    fun constant(name: String, value: String) = constantExpression(name, stringLiteral(value))

    fun constantExpression(name: String, expression: String, type: String = "string") = line("public const $type ${disambiguate(name)} = $expression;")

    /**
     * The singleton of the enclosing node class: a private constructor plus a static `Instance` field. The
     * initializer only ever runs the node's own initializers, never another node's, so static
     * initialisation cannot cycle.
     */
    fun singleton() {
        val typeName = enclosingTypes.last()
        line("public static readonly $typeName Instance = new();")
        line("private $typeName() { }")
    }

    fun readonlyProperty(name: String, type: String, initializer: String) = line("public $type ${disambiguate(name)} { get; } = $initializer;")

    fun expressionProperty(name: String, type: String, expression: String) = line("public $type ${disambiguate(name)} => $expression;")

    /**
     * A read-only list with one element per line. Expression-bodied, so it is built on access and never takes part
     * in static initialisation.
     */
    fun listProperty(name: String, elementType: String, elements: List<String>, modifiers: String = "public") {
        line("$modifiers System.Collections.Generic.IReadOnlyList<$elementType> $name => new $elementType[]")
        line("{")
        elements.forEach { line("    $it,") }
        line("};")
    }

    /**
     * C# rejects a member that shares its name with the type enclosing it (CS0542), which a BPMN element
     * called `Elements` or a timer event called `Timer` would otherwise produce. The JVM builders never hit
     * this because their members are UPPER_SNAKE_CASE or camelCase and so can never equal a PascalCase type name.
     */
    private fun disambiguate(name: String) = disambiguated(name, enclosingTypes.lastOrNull())

    /**
     * Emits an XML documentation comment. Multi-line text becomes one `<para>` per line so the
     * rendered IntelliSense tooltip keeps the breaks.
     */
    fun docComment(text: String) {
        val lines = text.trim().lines()
        line("/// <summary>")
        lines.forEach { line("/// ${if (lines.size > 1) "<para>${it.escapeXml()}</para>" else it.escapeXml()}") }
        line("/// </summary>")
    }

    fun <T> forEachSeparated(items: List<T>, action: (T) -> Unit) {
        items.forEachIndexed { index, item ->
            if (index > 0) line()
            action(item)
        }
    }

    fun render(): String = content.toString()

    private companion object {

        private const val INDENT = "    "

        private fun String.escapeXml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
