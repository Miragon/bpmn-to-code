package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

/**
 * Names of the types of `Miragon.BpmnToCode.Runtime` (in `packages/nuget`), the C# counterpart of
 * `bpmn-to-code-runtime`, which the generated code refers to. They are qualified with `global::`, so no type or
 * namespace of the consumer can shadow them.
 */
internal object CSharpRuntimeTypes {

    const val NAMESPACE = "global::Miragon.BpmnToCode.Runtime"

    const val BOUNDARY_EVENT = "$NAMESPACE.IBoundaryEvent"
    const val EVENT = "$NAMESPACE.IEvent"
    const val FLOW_NODE = "$NAMESPACE.IFlowNode"
    const val HAS_SUCCESSORS = "$NAMESPACE.IHasSuccessors"
    const val FLOW_SCOPE = "$NAMESPACE.IFlowScope"
    const val SUB_PROCESS = "$NAMESPACE.ISubProcess"

    val VARIABLE_DEFINITIONS_MEMBERS = setOf("All", "Inputs", "Outputs")

    fun facetInterface(typeName: String): String = "$NAMESPACE.I$typeName"
}
