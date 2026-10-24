package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.namespaceOf
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeMapper
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TemplateRenderer

/**
 * Generates the process API for a single BPMN process as a C# file, rendered by `templates/csharp/process-api.ftl`.
 *
 * The shape matches the Kotlin and Java output: process id and engine plus the typed `FlowNodes` navigation, built
 * on the types of the runtime package `Miragon.BpmnToCode.Runtime`.
 */
internal class CSharpProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        val nodes = api.flow?.nodes
        val mapper = FlowNodeMapper(CSharpFlowNodeSyntax(CSharpNodeNames(nodes.orEmpty().map { it.objectName })))
        val rendered = TemplateRenderer.render(
            templateName = "csharp/process-api.ftl",
            model = CSharpProcessApiModel(
                namespace = namespaceOf(api.packagePath.value),
                typeName = api.typeName,
                processId = stringLiteral(api.processId),
                processEngine = pascalCase(api.processEngine.name),
                flowNodes = nodes?.map { mapper.map(it) },
            ),
        )
        return SourceCode(rendered)
    }
}
