package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

/**
 * How values and names are written in generated Kotlin or Java code.
 */
internal interface JvmSyntax {

    fun stringLiteral(value: String): String

    fun identifier(name: String): String
}
