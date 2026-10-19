package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates one Kotlin object per kind of engine-global identifier, shared by all Process APIs of a run, rendered by
 * `templates/kotlin/shared-definitions.ftl`: a property per definition plus `all`.
 */
internal class KotlinSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val graph = SharedDefinitionsGraph.of(api.definitions)
        return graph.groups.map { group -> buildFile(group, api.packagePath) }
    }

    private fun buildFile(group: SharedDefinitionsGraph.Group, packagePath: String): GeneratedApiFile {
        val type = group.type
        val t = KotlinRuntimeTypes.references(generatedTypeNames = setOf(type.typeName))
        val shared = KotlinSharedReferences(t)
        val modifiers = if (type in CONSTANT_TYPES) "const " else ""
        val constants = group.entries.map { entry ->
            shared.property(type = type, modifiers = modifiers, name = identifier(entry.constantName), value = entry.value, code = entry.code)
        }
        val definitions = KotlinTemplateModel.SharedDefinitions(
            typeName = type.typeName,
            kdoc = KDOC.getValue(type),
            elementType = shared.elementType(type),
            constants = constants,
            t = t,
        )
        val body = TemplateRenderer.render("kotlin/shared-definitions.ftl", definitions)
        return GeneratedApiFile(
            fileName = "${type.typeName}.kt",
            packagePath = packagePath,
            content = KotlinSourceFiles.render(packagePath = packagePath, t = t, body = body),
            language = OutputLanguage.KOTLIN,
            processId = null,
        )
    }

    private companion object {
        const val CONSTANTS_FOR_ANNOTATIONS = "Kept as `const val String` because annotation arguments must be compile-time constants."

        val CONSTANT_TYPES = setOf(SharedDefinitionType.SERVICE_TASKS, SharedDefinitionType.PROCESS_VARIABLES)

        val KDOC = mapOf(
            SharedDefinitionType.SERVICE_TASKS to listOf(
                "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.",
                CONSTANTS_FOR_ANNOTATIONS,
            ),
            SharedDefinitionType.MESSAGES to listOf("BPMN message names used to correlate messages to running process instances."),
            SharedDefinitionType.SIGNALS to listOf("BPMN signal names broadcast and caught by signal events."),
            SharedDefinitionType.ERRORS to listOf("BPMN error definitions with name and code, as thrown and caught by the processes."),
            SharedDefinitionType.ESCALATIONS to listOf("BPMN escalation definitions with name and code, as thrown and caught by the processes."),
            SharedDefinitionType.PROCESS_VARIABLES to listOf(
                "Names of the process variables used in `@Variable(name = ProcessVariables.X)` annotations.",
                CONSTANTS_FOR_ANNOTATIONS,
            ),
        )
    }
}
