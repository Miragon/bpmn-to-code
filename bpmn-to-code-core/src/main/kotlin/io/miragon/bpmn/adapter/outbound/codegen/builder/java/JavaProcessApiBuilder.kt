package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectSelection
import io.miragon.bpmn.adapter.outbound.codegen.ApiObjectType
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraphFactory
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates the type-safe API contract for a single BPMN process as a Java class file, rendered by
 * `templates/java/process-api.ftl`. It refers to the types of the `bpmn-to-code-runtime` artifact.
 */
internal class JavaProcessApiBuilder : ProcessApiBuilder {

    override fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile {
        val typeName = modelApi.fileName()
        val graph = FlowGraphFactory.build(modelApi.model.graph, modelApi.model.definitions).takeIf {
            ApiObjectSelection.includes(ApiObjectType.FLOW, modelApi)
        }
        val t = JavaRuntimeTypes.references(generatedTypeNames = generatedTypeNamesOf(typeName, graph))
        val mapper = JavaFlowNodeMapper(t)
        val api = JavaTemplateModel.ProcessApi(
            typeName = typeName,
            processId = stringLiteral(modelApi.model.processId),
            processEngine = modelApi.targetEngine.name,
            flowNodes = graph?.nodes?.map { mapper.map(it) },
            t = t,
        )
        val body = TemplateRenderer.render("java/process-api.ftl", api)
        return GeneratedApiFile(
            fileName = "$typeName.java",
            packagePath = modelApi.packagePath,
            content = JavaSourceFiles.render(packageName = modelApi.packagePath, t = t, body = body),
            language = OutputLanguage.JAVA,
            processId = modelApi.model.processId,
        )
    }

    private fun generatedTypeNamesOf(typeName: String, graph: FlowGraph?): Set<String> {
        val nodeNames = graph?.nodes.orEmpty().map { it.objectName }
        return setOf(typeName) + HOLDER_NAMES + nodeNames
    }

    private companion object {
        val HOLDER_NAMES = setOf("FlowNodes", "Next", "Start", "Variables", "Inputs", "Outputs")
    }
}
