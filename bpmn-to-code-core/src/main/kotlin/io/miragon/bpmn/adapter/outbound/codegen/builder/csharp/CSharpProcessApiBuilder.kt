package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TemplateRenderer

/**
 * Generates the process API for a single BPMN process as a C# file, rendered by `templates/csharp/process-api.ftl`.
 *
 * The shape matches the Kotlin and Java output: process id and engine plus the typed `FlowNodes` navigation. There is
 * no C# runtime package; the runtime types every node references are inlined into the file as a nested `Runtime`
 * class, so the generated file has no dependencies at all.
 */
internal class CSharpProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        val rendered = TemplateRenderer.render(
            templateName = "csharp/process-api.ftl",
            model = CSharpProcessApiModel(
                namespace = api.packagePath.value,
                typeName = api.typeName.value,
                processId = stringLiteral(api.processId.value),
                processEngine = pascalCase(api.processEngine.name),
                flowNodes = api.flow?.nodes?.map { CSharpFlowNodeMapper.map(it) },
            ),
        )
        return SourceCode(rendered)
    }
}
