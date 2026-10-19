package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates the process API for a single BPMN process as a TypeScript module, rendered by
 * `templates/typescript/process-api.ftl`. It exports one constant named after the API; the node classes behind it stay
 * private to the module. The runtime types come from the npm package `@miragon/bpmn-to-code-runtime`.
 */
internal class TypeScriptProcessApiBuilder : ProcessApiBuilder {

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val typeName = modelApi.fileName()
        val shared = TypeScriptSharedReferences()
        val mapper = TypeScriptFlowNodeMapper(shared)
        val flowNodes = if (ApiObjectSelection.includes(ApiObjectType.FLOW, modelApi)) {
            FlowGraphFactory.build(modelApi.model.graph, modelApi.model.definitions).nodes.map { mapper.map(it) }
        } else {
            null
        }
        val api = TypeScriptTemplateModel.ProcessApi(
            runtimeModule = TypeScriptRuntime.MODULE,
            typeName = typeName,
            processId = stringLiteral(modelApi.model.processId),
            processEngine = stringLiteral(modelApi.targetEngine.name),
            sharedImports = shared.imports,
            flowNodes = flowNodes,
        )
        return GeneratedApiFile(
            fileName = "$typeName.ts",
            packagePath = modelApi.packagePath,
            content = TemplateRenderer.render("typescript/process-api.ftl", api),
            language = OutputLanguage.TYPESCRIPT,
            processId = modelApi.model.processId,
        )
    }
}
