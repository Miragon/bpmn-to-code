package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiBuilder
import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.FlowNodeMapper
import io.miragon.bpmn.domain.ProcessApiNamingException

/**
 * Generates the Process API of one BPMN process as a TypeScript module, rendered by
 * `templates/typescript/process-api.ftl`: one exported singleton class per element, then the API object, e.g.
 * ```
 * export const BikeLeasingProcessApi = {
 *   PROCESS_ID: "bikeLeasing",
 *   PROCESS_ENGINE: "ZEEBE",
 *   FlowNodes: {
 *     ServiceTaskSendContract: ServiceTaskSendContract.INSTANCE,
 *     …
 *   },
 * } as const;
 * ```
 * It is built on the npm runtime package `@miragon/bpmn-to-code-runtime`.
 */
internal class TypeScriptProcessApiBuilder : ProcessApiBuilder {

    override fun render(api: ProcessApiGraph): SourceCode {
        requireNoClassShadowsModuleName(api)
        val file = TypeScriptSourceFile()
        val mapper = FlowNodeMapper(TypeScriptFlowNodeSyntax(file))
        return file.render(
            templateName = "typescript/process-api.ftl",
            model = TypeScriptProcessApiModel(
                typeName = api.typeName,
                processId = stringLiteral(api.processId),
                processEngine = stringLiteral(api.processEngine.name),
                flowNodes = api.flow?.nodes?.map { mapper.map(it) },
                runtimeTypes = file.runtimeTypes,
                runtimeValues = file.runtimeValues,
            ),
        )
    }

    private fun requireNoClassShadowsModuleName(api: ProcessApiGraph) {
        val moduleNames = SharedDefinitionType.entries.map { it.typeName } +
            TypeScriptSourceFile.RUNTIME_ALIAS +
            api.typeName
        val shadowing = api.flow?.nodes.orEmpty().map { it.objectName }.filter { it in moduleNames }
        if (shadowing.isNotEmpty()) {
            throw ProcessApiNamingException("Element names clash with names the TypeScript module declares: $shadowing. Rename the elements.")
        }
    }
}
