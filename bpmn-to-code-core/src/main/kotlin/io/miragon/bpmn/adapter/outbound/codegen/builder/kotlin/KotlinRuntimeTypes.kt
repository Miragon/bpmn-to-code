package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * The types generated Kotlin code refers to: the `bpmn-to-code-runtime` types, and the Kotlin types that are only
 * written out when a generated type shadows them.
 */
internal object KotlinRuntimeTypes {

    private const val PACKAGE = "io.miragon.bpmn.runtime"

    private val RUNTIME_TYPES = listOf(
        "AbstractFlowNode",
        "AssociatedCompensationHandler",
        "AttachedBoundaryEvent",
        "BoundaryEvent",
        "BpmnElementType",
        "BpmnEngine",
        "BpmnErrorDefinition",
        "BpmnEscalationDefinition",
        "BpmnEventType",
        "BpmnTimer",
        "CallActivity",
        "CompensationThrowEvent",
        "ElementId",
        "ErrorEvent",
        "EscalationEvent",
        "Event",
        "FlowNode",
        "FlowScope",
        "HasJobType",
        "HasMessage",
        "HasSuccessors",
        "HasVariables",
        "InputOutputMapping",
        "MessageName",
        "ProcessId",
        "RegisteredVariableDefinitions",
        "SequenceFlow",
        "SequenceFlows",
        "SignalEvent",
        "SignalName",
        "TimerEvent",
        "TimerType",
        "VariableName",
    )

    private val QUALIFIED_NAMES = RUNTIME_TYPES.associateWith { "$PACKAGE.$it" } + mapOf(
        "Boolean" to "kotlin.Boolean",
        "String" to "kotlin.String",
        "List" to "kotlin.collections.List",
    )

    fun references(generatedTypeNames: Set<String>): TypeReferences = TypeReferences(
        qualifiedNames = QUALIFIED_NAMES,
        implicitPackages = setOf("kotlin", "kotlin.collections"),
        shadowingNames = generatedTypeNames,
    )
}
