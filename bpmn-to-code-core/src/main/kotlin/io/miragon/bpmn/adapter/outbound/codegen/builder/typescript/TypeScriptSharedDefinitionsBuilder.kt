package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.template.TemplateRenderer
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * Generates one module per kind of engine-global identifier, shared by all TypeScript process APIs of a run, rendered
 * by `templates/typescript/shared-definitions.ftl`.
 */
internal class TypeScriptSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile> {
        val graph = SharedDefinitionsGraph.of(api.definitions)
        return graph.groups.map { group -> buildFile(group, api.packagePath) }
    }

    private fun buildFile(group: SharedDefinitionsGraph.Group, packagePath: String): GeneratedApiFile {
        val type = group.type
        val shared = TypeScriptSharedReferences()
        val constants = group.entries.map { entry ->
            TypeScriptTemplateModel.Entry(name = identifier(entry.constantName), value = shared.value(type, name = entry.value, code = entry.code))
        }
        val definitions = TypeScriptTemplateModel.SharedDefinitions(
            runtimeModule = TypeScriptRuntime.MODULE,
            typeName = type.typeName,
            doc = DOCS.getValue(type),
            usesRuntime = type.carriesCode,
            elementType = ELEMENT_TYPES.getValue(type),
            constants = constants,
        )
        return file(typeName = type.typeName, packagePath = packagePath, content = TemplateRenderer.render("typescript/shared-definitions.ftl", definitions))
    }

    private fun file(typeName: String, packagePath: String, content: String): GeneratedApiFile = GeneratedApiFile(
        fileName = "$typeName.ts",
        packagePath = packagePath,
        content = content,
        language = OutputLanguage.TYPESCRIPT,
        processId = null,
    )

    private companion object {
        val ELEMENT_TYPES = mapOf(
            SharedDefinitionType.SERVICE_TASKS to "string",
            SharedDefinitionType.MESSAGES to "string",
            SharedDefinitionType.SIGNALS to "string",
            SharedDefinitionType.ERRORS to "Runtime.BpmnErrorDefinition",
            SharedDefinitionType.ESCALATIONS to "Runtime.BpmnEscalationDefinition",
            SharedDefinitionType.PROCESS_VARIABLES to "string",
        )

        val DOCS = mapOf(
            SharedDefinitionType.SERVICE_TASKS to "Job worker task types: the task type a TypeScript worker subscribes to.",
            SharedDefinitionType.MESSAGES to "BPMN message names used to correlate messages to running process instances.",
            SharedDefinitionType.SIGNALS to "BPMN signal names broadcast and caught by signal events.",
            SharedDefinitionType.ERRORS to "BPMN error definitions with name and code, as thrown and caught by the processes.",
            SharedDefinitionType.ESCALATIONS to "BPMN escalation definitions with name and code, as thrown and caught by the processes.",
            SharedDefinitionType.PROCESS_VARIABLES to "Names of the process variables: the keys a TypeScript worker reads and writes.",
        )
    }
}
