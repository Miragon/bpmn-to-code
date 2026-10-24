package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them: `ProcessId("bikeLeasing")` in Kotlin, `new ProcessId("bikeLeasing")`
 * in Java. Types are read through [types].
 *
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class JvmProcessApiModel(
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNodeModel>?,
    val types: TypeReferences,
)
