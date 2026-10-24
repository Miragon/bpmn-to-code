package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeMapper
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.JvmFlowNodeSyntax
import io.miragon.bpmn.domain.ProcessApiNames

/**
 * Generates the type-safe API contract for a single BPMN process as a Kotlin object or a Java class, rendered by
 * `templates/<language>/process-api.ftl`. It refers to the types of the `bpmn-to-code-runtime` artifact.
 */
internal class JvmProcessApiBuilder(private val language: JvmLanguage) : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        val file = language.sourceFile(packagePath = api.packagePath, generatedTypeNames = generatedTypeNamesOf(api))
        val mapper = FlowNodeMapper(JvmFlowNodeSyntax(file.types, language.syntax))
        return file.render(
            templateName = "${language.templateDirectory}/process-api.ftl",
            model = JvmProcessApiModel(
                typeName = api.typeName,
                processId = language.syntax.stringLiteral(api.processId),
                processEngine = api.processEngine.name,
                flowNodes = api.flow?.nodes?.map { mapper.map(it) },
                types = file.types,
            ),
        )
    }

    /**
     * The names of the types the Process API declares itself. A referenced type of the same simple name would be
     * shadowed by them, so it is written fully qualified.
     */
    private fun generatedTypeNamesOf(api: ProcessApiGraph): Set<String> {
        val nodeNames = api.flow?.nodes.orEmpty().map { it.objectName }
        return setOf(api.typeName) + ProcessApiNames.HOLDER_NAMES + nodeNames
    }
}
