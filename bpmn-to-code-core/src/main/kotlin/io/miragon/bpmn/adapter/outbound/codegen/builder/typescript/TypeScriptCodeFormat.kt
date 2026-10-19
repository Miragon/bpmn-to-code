package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

internal object TypeScriptCodeFormat {

    private val identifier = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

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

    fun propertyKey(name: String): String = if (identifier.matches(name)) name else stringLiteral(name)

    fun nameAndCode(name: String, code: String): String = "{ name: ${stringLiteral(name)}, code: ${stringLiteral(code)} }"
}
