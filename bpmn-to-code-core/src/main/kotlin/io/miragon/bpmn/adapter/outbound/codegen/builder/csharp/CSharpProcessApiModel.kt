package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them. Runtime types are qualified with [runtime],
 * e.g. `global::Miragon.BpmnToCode.Runtime.ElementId`.
 *
 * @property namespace the package path in PascalCase, e.g. `Io.Miravelo.Process`
 * @property processEngine a `BpmnEngine` member, e.g. `Zeebe`
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class CSharpProcessApiModel(
    val namespace: String,
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNodeModel>?,
) {

    val runtime: String = CSharpRuntimeTypes.NAMESPACE
}
