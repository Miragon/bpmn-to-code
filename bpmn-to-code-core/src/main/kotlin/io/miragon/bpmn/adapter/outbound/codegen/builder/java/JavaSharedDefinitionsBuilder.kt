package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates one Java class per kind of engine-global identifier, shared by all Process APIs of a run, rendered by
 * `templates/java/shared-definitions.ftl`: a constant per definition plus an `all()` accessor.
 */
internal class JavaSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val graph = SharedDefinitionsGraph.of(api.definitions)
        return graph.groups.map { group -> buildFile(group, api.packagePath) }
    }

    private fun buildFile(group: SharedDefinitionsGraph.Group, packagePath: String): GeneratedApiFile {
        val type = group.type
        val t = JavaRuntimeTypes.references(generatedTypeNames = setOf(type.typeName))
        val shared = JavaSharedReferences(t)
        val elementType = shared.elementType(type)
        val constants = group.entries.map { entry ->
            JavaTemplateModel.Constant(
                type = elementType,
                name = identifier(entry.constantName),
                value = shared.value(type, name = entry.value, code = entry.code),
            )
        }
        val definitions = JavaTemplateModel.SharedDefinitions(
            typeName = type.typeName,
            javadoc = JAVADOC.getValue(type),
            elementType = elementType,
            constants = constants,
            t = t,
        )
        val body = TemplateRenderer.render("java/shared-definitions.ftl", definitions)
        return GeneratedApiFile(
            fileName = "${type.typeName}.java",
            packagePath = packagePath,
            content = JavaSourceFiles.render(packageName = packagePath, t = t, body = body),
            language = OutputLanguage.JAVA,
            processId = null,
        )
    }

    private companion object {
        const val CONSTANTS_FOR_ANNOTATIONS = "Kept as {@code public static final String} because annotation arguments must be compile-time constants."

        val JAVADOC = mapOf(
            SharedDefinitionType.SERVICE_TASKS to listOf(
                "Job worker task types used in {@code @JobWorker(type = ServiceTasks.X)} annotations.",
                CONSTANTS_FOR_ANNOTATIONS,
            ),
            SharedDefinitionType.MESSAGES to listOf("BPMN message names used to correlate messages to running process instances."),
            SharedDefinitionType.SIGNALS to listOf("BPMN signal names broadcast and caught by signal events."),
            SharedDefinitionType.ERRORS to listOf("BPMN error definitions with name and code, as thrown and caught by the processes."),
            SharedDefinitionType.ESCALATIONS to listOf("BPMN escalation definitions with name and code, as thrown and caught by the processes."),
            SharedDefinitionType.PROCESS_VARIABLES to listOf(
                "Names of the process variables used in {@code @Variable(name = ProcessVariables.X)} annotations.",
                CONSTANTS_FOR_ANNOTATIONS,
            ),
        )
    }
}
