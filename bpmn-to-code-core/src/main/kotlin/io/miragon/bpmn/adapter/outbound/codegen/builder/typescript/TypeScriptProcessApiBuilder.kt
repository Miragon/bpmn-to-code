package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile

/**
 * Generates the Process API of one BPMN process as a TypeScript module: one exported singleton class per element,
 * then the API object, whose `FlowNodes` holds the instances. Nodes reach each other through getters, so loading the
 * module never runs into a class that is not defined yet.
 */
internal class TypeScriptProcessApiBuilder : ProcessApiBuilder {

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val graph = flowGraphOf(modelApi)
        return TypeScriptSourceFile.render(
            typeName = modelApi.fileName(),
            packagePath = modelApi.packagePath,
            processId = modelApi.model.processId,
        ) { writer ->
            val flowWriter = TypeScriptFlowWriter(writer)
            graph?.let { flowWriter.writeNodeClasses(it) }
            writer.exportedConstant(modelApi.fileName()) {
                writer.property("PROCESS_ID", stringLiteral(modelApi.model.processId))
                writer.property("PROCESS_ENGINE", stringLiteral(modelApi.targetEngine.name))
                graph?.let { flowWriter.writeFlowNodes(it) }
            }
        }
    }

    private fun flowGraphOf(modelApi: BpmnModelApi): FlowGraph? {
        if (!ApiObjectSelection.includes(ApiObjectType.FLOW, modelApi)) {
            return null
        }
        return FlowGraphFactory.build(modelApi.model.graph, modelApi.model.definitions)
    }
}
