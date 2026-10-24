package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import freemarker.template.TemplateHashModel
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeModel

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them: `PROCESS_ID: "bikeLeasing"`. Runtime names are read through
 * [runtimeTypes] and [runtimeValues].
 *
 * @property processEngine e.g. `"ZEEBE"`
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class TypeScriptProcessApiModel(
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNodeModel>?,
    val runtimeTypes: TemplateHashModel,
    val runtimeValues: TemplateHashModel,
)
