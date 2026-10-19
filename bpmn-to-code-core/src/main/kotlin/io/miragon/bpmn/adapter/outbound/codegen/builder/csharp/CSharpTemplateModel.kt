package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

/**
 * What the C# templates render. Every string is finished C# source: literals are quoted and escaped, names are
 * disambiguated and references qualified, so the templates only place them.
 */
internal object CSharpTemplateModel {

    /**
     * @property runtime the namespace of the runtime package, qualified from `global::`.
     * @property flowNodes `null` when the process API has no `FlowNodes` section.
     */
    data class ProcessApi(
        val runtime: String,
        val namespace: String,
        val typeName: String,
        val processId: String,
        val processEngine: String,
        val flowNodes: List<FlowNode>?,
    )

    data class FlowNode(
        val className: String,
        val interfaces: String,
        val elementId: String,
        val elementType: String,
        val eventType: String?,
        val name: String,
        val properties: List<Member>,
        val variables: Variables?,
        val inputs: List<Member>,
        val outputs: List<Member>,
        val successors: List<Member>,
        val interiorStarts: List<Member>,
    )

    data class Variables(val members: List<Member>, val all: String)

    /**
     * A property: `public type name => value;` when [expressionBodied], else `public type name { get; } = value;`.
     */
    data class Member(val type: String, val name: String, val value: String, val expressionBodied: Boolean)

    /**
     * @property modifiers `const` or `static readonly`, the same for every constant of a class.
     */
    data class SharedDefinitions(
        val namespace: String,
        val typeName: String,
        val doc: String,
        val modifiers: String,
        val elementType: String,
        val constants: List<Constant>,
    )

    data class Constant(val name: String, val value: String)
}
