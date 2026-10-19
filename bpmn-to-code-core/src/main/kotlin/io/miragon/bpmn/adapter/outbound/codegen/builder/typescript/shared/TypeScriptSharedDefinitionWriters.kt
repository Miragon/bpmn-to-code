package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.nameAndCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.stringLiteral

internal object TypeScriptSharedDefinitionWriters {

    val all: List<TypeScriptSharedDefinitionWriter<*>> = listOf(
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.SERVICE_TASKS,
            doc = "Job worker task types, the type a worker subscribes to.",
            definitionsOf = { definitions -> definitions.serviceTasks },
            literalOf = { value -> stringLiteral(value) },
        ),
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.MESSAGES,
            doc = "BPMN message names used to correlate messages to running process instances.",
            definitionsOf = { definitions -> definitions.messages },
            literalOf = { value -> stringLiteral(value) },
        ),
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.SIGNALS,
            doc = "BPMN signal names broadcast and caught by signal events.",
            definitionsOf = { definitions -> definitions.signals },
            literalOf = { value -> stringLiteral(value) },
        ),
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.ERRORS,
            doc = "BPMN error definitions with name and code, as thrown and caught by the processes.",
            definitionsOf = { definitions -> definitions.errors },
            literalOf = { (name, code) -> nameAndCode(name, code) },
            elementTypeOf = { writer -> writer.imports.runtimeType("BpmnErrorDefinition") },
        ),
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.ESCALATIONS,
            doc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.",
            definitionsOf = { definitions -> definitions.escalations },
            literalOf = { (name, code) -> nameAndCode(name, code) },
            elementTypeOf = { writer -> writer.imports.runtimeType("BpmnEscalationDefinition") },
        ),
        TypeScriptSharedDefinitionWriter(
            type = SharedDefinitionType.PROCESS_VARIABLES,
            doc = "Names of the process variables, the keys a worker reads and writes.",
            definitionsOf = { definitions -> definitions.processVariables },
            literalOf = { value -> stringLiteral(value) },
        ),
    )
}
