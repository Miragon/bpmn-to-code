package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * The Process API of one process as the builders render it: its name, package, process id and engine, plus the
 * [FlowGraph] when [ApiObjectSelection] includes the flow. Computed once and rendered per output language.
 */
internal data class ProcessApiGraph(
    val typeName: String,
    val packagePath: String,
    val processId: String,
    val processEngine: ProcessEngine,
    val flow: FlowGraph?,
) {

    companion object {

        fun of(modelApi: BpmnModelApi): ProcessApiGraph {
            val model = modelApi.model
            val includesFlow = ApiObjectSelection.includes(ApiObjectType.FLOW, modelApi)
            return ProcessApiGraph(
                typeName = modelApi.fileName(),
                packagePath = modelApi.packagePath,
                processId = model.processId,
                processEngine = modelApi.targetEngine,
                flow = if (includesFlow) FlowGraphFactory.build(model.graph, model.definitions) else null,
            )
        }
    }
}
