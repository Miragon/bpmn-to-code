package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

/**
 * Names of the C# counterpart of `bpmn-to-code-runtime`. The types themselves are the fixed text
 * `templates/csharp/runtime-types.cs`, emitted verbatim into every generated file as a nested `Runtime` class of the
 * process API. Nesting keeps two generated files in one assembly from clashing and spares consumers a package: they
 * reference `MyProcessApi.Runtime.ElementId`.
 */
internal object CSharpRuntimeTypes {

    const val CLASS_NAME = "Runtime"

    const val BOUNDARY_EVENT = "$CLASS_NAME.IBoundaryEvent"
    const val EVENT = "$CLASS_NAME.IEvent"
    const val FLOW_NODE = "$CLASS_NAME.IFlowNode"

    val VARIABLE_DEFINITIONS_MEMBERS = setOf("All", "Inputs", "Outputs")

    fun facetInterface(typeName: String): String = "$CLASS_NAME.I$typeName"
}
