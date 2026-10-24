package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.ProcessApiNames
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * The Process API of one process as the builders render it: its name, package, process id and the engine the run
 * generates for, plus the [FlowGraph] unless the process has no flow nodes. Computed once and rendered per output
 * language.
 */
internal data class ProcessApiGraph(
    val typeName: String,
    val packagePath: PackagePath,
    val processId: String,
    val processEngine: ProcessEngine,
    val flow: FlowGraph?,
) {

    companion object {

        fun of(model: ProcessModel, request: ProcessApiRequest): ProcessApiGraph {
            val hasFlow = model.allFlowNodes.isNotEmpty()
            return ProcessApiGraph(
                typeName = ProcessApiNames.apiTypeName(model),
                packagePath = PackagePath(request.packagePath),
                processId = model.processId,
                processEngine = request.engine,
                flow = if (hasFlow) FlowGraphFactory.build(model.graph, model.definitions) else null,
            )
        }
    }
}
