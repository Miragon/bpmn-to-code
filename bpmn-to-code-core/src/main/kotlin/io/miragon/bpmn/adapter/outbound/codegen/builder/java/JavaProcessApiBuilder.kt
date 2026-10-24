package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeMapper
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.GeneratedTypeNames
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.JvmFlowNodeSyntax

/**
 * Generates the type-safe API contract for a single BPMN process as a Java class file, rendered by
 * `templates/java/process-api.ftl`. It refers to the types of the `bpmn-to-code-runtime` artifact.
 */
internal class JavaProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        val file = JavaSourceFile.of(packagePath = api.packagePath, generatedTypeNames = GeneratedTypeNames.of(api))
        val mapper = FlowNodeMapper(JvmFlowNodeSyntax(file.types, JavaSyntax))
        return file.render(
            templateName = "java/process-api.ftl",
            model = JavaProcessApiModel(
                typeName = api.typeName,
                processId = stringLiteral(api.processId),
                processEngine = api.processEngine.name,
                flowNodes = api.flow?.nodes?.map { mapper.map(it) },
                types = file.types,
            ),
        )
    }
}
