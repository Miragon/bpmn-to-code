package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.propertyKey

internal class TypeScriptWriter {

    private val content = StringBuilder()
    private var indentLevel = 0

    val imports = TypeScriptImports()

    fun line(text: String = "") {
        if (text.isEmpty()) {
            content.append('\n')
        } else {
            content.append(INDENT.repeat(indentLevel)).append(text).append('\n')
        }
    }

    fun docComment(text: String) {
        line("/**")
        text.trim().lines().forEach { line(" * $it") }
        line(" */")
    }

    fun exportedConstant(name: String, body: () -> Unit) {
        line("export const $name = {")
        indented(body)
        line("} as const;")
    }

    fun exportedClass(name: String, implements: List<String>, body: () -> Unit) {
        line("export class $name implements ${implements.joinToString(", ")} {")
        indented(body)
        line("}")
    }

    fun objectProperty(key: String, body: () -> Unit) {
        line("${propertyKey(key)}: {")
        indented(body)
        line("},")
    }

    fun property(key: String, value: String) = line("${propertyKey(key)}: $value,")

    fun field(name: String, value: String) = line("readonly $name = $value;")

    /**
     * A class field holding an object literal, optionally wrapped: `prefix` goes before its opening brace and `suffix`
     * after its closing one, as in `Runtime.variableDefinitions({ … })` or `{ … } as const`.
     */
    fun objectField(name: String, prefix: String = "", suffix: String = "", body: () -> Unit) {
        line("readonly $name = $prefix{")
        indented(body)
        line("}$suffix;")
    }

    fun getter(name: String, type: String, expression: String) {
        line("get $name(): $type {")
        indented { line("return $expression;") }
        line("}")
    }

    fun objectGetter(key: String, type: String, expression: String) {
        line("get ${propertyKey(key)}(): $type {")
        indented { line("return $expression;") }
        line("},")
    }

    fun listGetter(key: String, type: String, elements: List<String>) {
        line("get ${propertyKey(key)}(): $type {")
        indented {
            line("return [")
            indented { elements.forEach { line("$it,") } }
            line("];")
        }
        line("},")
    }

    fun render(): String = content.toString()

    private fun indented(body: () -> Unit) {
        indentLevel++
        body()
        indentLevel--
    }

    private companion object {
        private const val INDENT = "  "
    }
}
