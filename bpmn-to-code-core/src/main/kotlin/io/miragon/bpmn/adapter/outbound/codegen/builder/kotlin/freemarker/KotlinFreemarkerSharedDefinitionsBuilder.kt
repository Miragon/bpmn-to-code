package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSharedDefinitionsModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Generates one Kotlin object per kind of engine-global identifier, shared by all Process APIs of a run, rendered by
 * `templates/kotlin/shared-definitions.ftl`, e.g.
 * ```
 * object Messages {
 *   val MIRAVELO_CONTRACT_SIGNED: MessageName = MessageName("miravelo.contractSigned")
 *
 *   val all: List<MessageName> = listOf(
 *     MIRAVELO_CONTRACT_SIGNED,
 *   )
 * }
 * ```
 */
internal class KotlinFreemarkerSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: PackagePath): SourceCode {
        val definitionType = group.type
        val file = KotlinSourceFile.of(packagePath = packagePath, generatedTypeNames = setOf(definitionType.typeName))
        return file.render(
            templateName = "kotlin/shared-definitions.ftl",
            model = KotlinSharedDefinitionsModel(
                typeName = definitionType.typeName,
                doc = docOf(definitionType),
                shape = definitionType.shape,
                elementType = file.types.of(definitionType.runtimeTypeName),
                constants = group.entries.map { constantOf(it) },
                types = file.types,
            ),
        )
    }

    private fun constantOf(entry: SharedDefinitionsGraph.Entry): Constant = Constant(
        name = identifier(entry.constantName),
        value = stringLiteral(entry.value),
        code = entry.code?.let { stringLiteral(it) },
    )

    private fun docOf(type: SharedDefinitionType): List<String> = when (type) {
        SERVICE_TASKS -> listOf(
            "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.",
            CONSTANTS_FOR_ANNOTATIONS,
        )

        MESSAGES -> listOf("BPMN message names used to correlate messages to running process instances.")

        SIGNALS -> listOf("BPMN signal names broadcast and caught by signal events.")

        ERRORS -> listOf("BPMN error definitions with name and code, as thrown and caught by the processes.")

        ESCALATIONS -> listOf("BPMN escalation definitions with name and code, as thrown and caught by the processes.")

        PROCESS_VARIABLES -> listOf(
            "Names of the process variables used in `@Variable(name = ProcessVariables.X)` annotations.",
            CONSTANTS_FOR_ANNOTATIONS,
        )
    }

    private companion object {
        const val CONSTANTS_FOR_ANNOTATIONS = "Kept as `const val String` " +
            "because annotation arguments must be compile-time constants."
    }
}
