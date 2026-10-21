package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.GeneratedTypeNames

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object file, rendered by
 * `templates/kotlin/process-api.ftl`. It refers to the types of the `bpmn-to-code-runtime` artifact.
 */
internal class KotlinFreemarkerProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        val file = KotlinSourceFile.of(packagePath = api.packagePath, generatedTypeNames = GeneratedTypeNames.of(api))
        val mapper = KotlinFlowNodeMapper(file.types)
        return file.render(
            templateName = "kotlin/process-api.ftl",
            model = KotlinProcessApiModel(
                typeName = api.typeName.value,
                processId = stringLiteral(api.processId.value),
                processEngine = api.processEngine.name,
                flowNodes = api.flow?.nodes?.map { mapper.map(it) },
                types = file.types,
            ),
        )
    }
}
