package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * What the Java templates render. Every string is finished Java source: literals are quoted and escaped, and type
 * references are resolved through [TypeReferences], which the templates read as `t`.
 */
internal object JavaTemplateModel {

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
        val className: String,
        val accessorName: String,
        val interfaces: String,
        val elementId: String,
        val elementType: String,
        val name: String?,
        val getters: List<Getter>,
        val variables: Variables?,
        val inputs: List<Constant>,
        val outputs: List<Constant>,
        val successors: List<Accessor>,
        val interiorStarts: List<Accessor>,
    )

    data class Getter(val type: String, val name: String, val value: String, val overrides: Boolean)

    data class Variables(val constants: List<Constant>, val all: String)

    data class Constant(val type: String, val name: String, val value: String)

    data class Accessor(val type: String, val name: String, val value: String)

    data class SharedDefinitions(
        val typeName: String,
        val javadoc: List<String>,
        val elementType: String,
        val constants: List<Constant>,
        val t: TypeReferences,
    )

    data class SourceFile(val packageName: String, val imports: List<String>, val body: String)
}
