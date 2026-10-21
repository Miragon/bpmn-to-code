package io.miragon.bpmn.adapter.outbound.codegen.freemarker

/**
 * How values and names are written in generated Kotlin or Java code.
 */
internal interface JvmSyntax {

    fun stringLiteral(value: String): String

    fun identifier(name: String): String
}
