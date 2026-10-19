package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpTemplateModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates one C# static class per kind of engine-global identifier, shared by all Process APIs of a run, rendered
 * by `templates/csharp/shared-definitions.ftl`: a field per definition plus `All`.
 */
internal class CSharpSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val graph = SharedDefinitionsGraph.of(api.definitions)
        return graph.groups.map { group -> buildFile(group, api.packagePath) }
    }

    private fun buildFile(group: SharedDefinitionsGraph.Group, packagePath: String): GeneratedApiFile {
        val type = group.type
        val constants = group.entries.map { entry ->
            Constant(
                name = CSharpSharedReferences.memberName(type, entry.constantName),
                value = CSharpSharedReferences.value(type = type, value = entry.value, code = entry.code),
            )
        }
        val definitions = CSharpTemplateModel.SharedDefinitions(
            namespace = packagePath,
            typeName = type.typeName,
            doc = DOCS.getValue(type),
            modifiers = CSharpSharedReferences.modifiers(type),
            elementType = CSharpSharedReferences.elementType(type),
            constants = constants,
        )
        return GeneratedApiFile(
            fileName = "${type.typeName}.cs",
            packagePath = packagePath,
            content = TemplateRenderer.render("csharp/shared-definitions.ftl", definitions),
            language = OutputLanguage.CSHARP,
            processId = null,
        )
    }

    private companion object {
        val DOCS = mapOf(
            SharedDefinitionType.SERVICE_TASKS to "Job worker task types — the task type a C# worker subscribes to.",
            SharedDefinitionType.MESSAGES to "BPMN message names used to correlate messages to running process instances.",
            SharedDefinitionType.SIGNALS to "BPMN signal names broadcast and caught by signal events.",
            SharedDefinitionType.ERRORS to "BPMN error definitions with name and code, as thrown and caught by the processes.",
            SharedDefinitionType.ESCALATIONS to "BPMN escalation definitions with name and code, as thrown and caught by the processes.",
            SharedDefinitionType.PROCESS_VARIABLES to "Names of the process variables — the keys a C# worker reads and writes.",
        )
    }
}
