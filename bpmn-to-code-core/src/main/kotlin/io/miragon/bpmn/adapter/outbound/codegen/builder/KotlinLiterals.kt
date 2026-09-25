package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.CodeBlock

internal fun stringLiteral(value: String): CodeBlock = if (value.contains("\${")) {
    CodeBlock.of("\$\$\"\"\"%L\"\"\"", value)
} else {
    CodeBlock.of("%S", value)
}
