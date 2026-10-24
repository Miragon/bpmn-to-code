package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

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
import io.miragon.bpmn.adapter.outbound.codegen.builder.jvm.JvmSharedDefinitionsModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Generates one Kotlin object or Java class per kind of engine-global identifier, shared by all Process APIs of a run,
 * rendered by `templates/<language>/shared-definitions.ftl`, e.g. in Kotlin
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
internal class JvmSharedDefinitionsBuilder(private val language: JvmLanguage) : SharedDefinitionsBuilder {

    override fun render(group: SharedDefinitionsGraph.Group, packagePath: PackagePath): SourceCode {
        val definitionType = group.type
        val file = language.sourceFile(packagePath = packagePath, generatedTypeNames = setOf(definitionType.typeName))
        return file.render(
            templateName = "${language.templateDirectory}/shared-definitions.ftl",
            model = JvmSharedDefinitionsModel(
                typeName = definitionType.typeName,
                doc = docOf(definitionType),
                shape = definitionType.shape,
                elementType = file.types.of(definitionType.runtimeTypeName),
                constants = group.entries.map { constantOf(it) },
                types = file.types.names,
            ),
        )
    }

    private fun constantOf(entry: SharedDefinitionsGraph.Entry): Constant = Constant(
        name = language.syntax.identifier(entry.constantName),
        value = language.syntax.stringLiteral(entry.value),
        code = entry.code?.let { language.syntax.stringLiteral(it) },
    )

    private fun docOf(type: SharedDefinitionType): List<String> = when (type) {
        SERVICE_TASKS -> listOf(
            "Job worker task types used in ${language.codeSpan("@JobWorker(type = ServiceTasks.X)")} annotations.",
            language.constantsForAnnotations(),
        )

        MESSAGES -> listOf("BPMN message names used to correlate messages to running process instances.")

        SIGNALS -> listOf("BPMN signal names broadcast and caught by signal events.")

        ERRORS -> listOf("BPMN error definitions with name and code, as thrown and caught by the processes.")

        ESCALATIONS -> listOf("BPMN escalation definitions with name and code, as thrown and caught by the processes.")

        PROCESS_VARIABLES -> listOf(
            "Names of the process variables used in ${language.codeSpan("@Variable(name = ProcessVariables.X)")} annotations.",
            language.constantsForAnnotations(),
        )
    }
}
