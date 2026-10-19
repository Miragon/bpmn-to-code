package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates the process API for a single BPMN process as a C# file, rendered by `templates/csharp/process-api.ftl`.
 *
 * The shape matches the Kotlin and Java output: process id and engine plus the typed `FlowNodes` navigation, built from
 * the types of the NuGet package `Miragon.BpmnToCode.Runtime`.
 */
internal class CSharpProcessApiBuilder : ProcessApiBuilder {

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val typeName = modelApi.fileName()
        val api = CSharpTemplateModel.ProcessApi(
            runtime = CSharpRuntimeTypes.NAMESPACE,
            namespace = modelApi.packagePath,
            typeName = typeName,
            processId = stringLiteral(modelApi.model.processId),
            processEngine = CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.BPMN_ENGINE, modelApi.targetEngine.name),
            flowNodes = flowNodesOf(modelApi),
        )
        return GeneratedApiFile(
            fileName = "$typeName.cs",
            packagePath = modelApi.packagePath,
            content = TemplateRenderer.render("csharp/process-api.ftl", api),
            language = OutputLanguage.CSHARP,
            processId = modelApi.model.processId,
        )
    }

    private fun flowNodesOf(modelApi: BpmnModelApi): List<CSharpTemplateModel.FlowNode>? {
        if (!ApiObjectSelection.includes(ApiObjectType.FLOW, modelApi)) return null
        val graph = FlowGraphFactory.build(modelApi.model.graph, modelApi.model.definitions)
        return graph.nodes.map { CSharpFlowNodeMapper.map(it) }
    }
}
