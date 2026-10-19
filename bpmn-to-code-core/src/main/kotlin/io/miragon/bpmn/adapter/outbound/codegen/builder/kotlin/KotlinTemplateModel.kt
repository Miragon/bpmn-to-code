package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * What the Kotlin templates render. Every string is finished Kotlin source: literals are quoted and escaped, names
 * escaped where needed, and type references resolved through [TypeReferences], which the templates read as `t`.
 */
internal object KotlinTemplateModel {

    /**
     * @property flowNodes `null` when the process API has no `FlowNodes` section.
     */
    data class ProcessApi(
        val typeName: String,
        val processId: String,
        val processEngine: String,
        val flowNodes: List<FlowNode>?,
        val t: TypeReferences,
    )

    data class FlowNode(
        val objectName: String,
        val superArguments: List<Argument>,
        val supertypes: String,
        val properties: List<Property>,
        val variables: List<Property>,
        val inputs: List<Property>,
        val outputs: List<Property>,
        val successors: List<Property>,
        val interiorStarts: List<Property>,
    )

    /**
     * `modifiers val name: type` followed by `= value`, by `= ` and a multi-line [call], or by a [getter] on the next
     * line.
     */
    data class Property(
        val modifiers: String,
        val name: String,
        val type: String,
        val value: String? = null,
        val call: Call? = null,
        val getter: Getter? = null,
    )

    /**
     * `function(` with one named argument per line, `)`. An argument is either a [Argument.value] or a nested call.
     */
    data class Call(val function: String, val arguments: List<Argument>)

    data class Argument(val name: String, val value: String? = null, val calls: List<Call>? = null)

    /**
     * `get() = value`, or `get() = ` and a multi-line [call].
     */
    data class Getter(val value: String? = null, val call: Call? = null)

    data class SharedDefinitions(
        val typeName: String,
        val kdoc: List<String>,
        val elementType: String,
        val constants: List<Property>,
        val t: TypeReferences,
    )

    data class SourceFile(val packageName: String, val imports: List<String>, val body: String)
}
