package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

/**
 * What the TypeScript templates render. Every string is finished TypeScript source: literals are quoted and escaped,
 * and runtime types are referenced through the `Runtime` namespace import of [TypeScriptRuntime.MODULE], so no
 * generated name can shadow them.
 */
internal object TypeScriptTemplateModel {

    /**
     * @property flowNodes `null` when the process API has no `FlowNodes` section.
     */
    data class ProcessApi(
        val runtimeModule: String,
        val typeName: String,
        val processId: String,
        val processEngine: String,
        val sharedImports: List<String>,
        val flowNodes: List<FlowNode>?,
    )

    data class FlowNode(
        val className: String,
        val interfaces: String,
        val elementId: String,
        val elementType: String,
        val eventType: String?,
        val name: String,
        val fields: List<Field>,
        val attachedTo: String?,
        val variables: List<Entry>,
        val inputs: List<Entry>,
        val outputs: List<Entry>,
        val successors: List<Getter>,
        val interiorStarts: List<Getter>,
    )

    /**
     * `readonly name[: type] = value;`
     */
    data class Field(val name: String, val type: String?, val value: String)

    /**
     * `get name(): type { return value; }`
     */
    data class Getter(val name: String, val type: String, val value: String)

    /**
     * `name: value,` in an object literal.
     */
    data class Entry(val name: String, val value: String)

    data class SharedDefinitions(
        val runtimeModule: String,
        val typeName: String,
        val doc: String,
        val usesRuntime: Boolean,
        val elementType: String,
        val constants: List<Entry>,
    )
}
