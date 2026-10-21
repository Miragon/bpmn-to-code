package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.Shape
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSharedDefinitionsModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Generates one exported `as const` object per kind of engine-global identifier, shared by all Process APIs of a run,
 * rendered by `templates/typescript/shared-definitions.ftl`, e.g.
 * ```
 * export const Messages = {
 *   MIRAVELO_CONTRACT_SIGNED: "miravelo.contractSigned",
 *   get all(): readonly string[] {
 *     return [
 *       Messages.MIRAVELO_CONTRACT_SIGNED,
 *     ];
 *   },
 * } as const;
 * ```
 */
internal class TypeScriptSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: PackagePath): SourceCode {
        val file = TypeScriptSourceFile()
        return file.render(
            templateName = "typescript/shared-definitions.ftl",
            model = TypeScriptSharedDefinitionsModel(
                typeName = group.type.typeName,
                doc = docOf(group.type),
                elementType = elementTypeOf(group.type, file),
                constants = group.entries.map { constantOf(it) },
            ),
        )
    }

    /**
     * Names are plain strings in TypeScript; only errors and escalations have a runtime type.
     */
    private fun elementTypeOf(type: SharedDefinitionType, file: TypeScriptSourceFile): String = when (type.shape) {
        Shape.PLAIN_NAME, Shape.TYPED_NAME -> "string"
        Shape.NAME_AND_CODE -> file.runtimeType(type.runtimeTypeName)
    }

    private fun constantOf(entry: SharedDefinitionsGraph.Entry): Constant = Constant(
        key = identifier(entry.constantName),
        value = stringLiteral(entry.value),
        code = entry.code?.let { stringLiteral(it) },
    )

    private fun docOf(type: SharedDefinitionType): String = when (type) {
        SERVICE_TASKS -> "Job worker task types, the type a worker subscribes to."
        MESSAGES -> "BPMN message names used to correlate messages to running process instances."
        SIGNALS -> "BPMN signal names broadcast and caught by signal events."
        ERRORS -> "BPMN error definitions with name and code, as thrown and caught by the processes."
        ESCALATIONS -> "BPMN escalation definitions with name and code, as thrown and caught by the processes."
        PROCESS_VARIABLES -> "Names of the process variables, the keys a worker reads and writes."
    }
}
