package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.template.TypeReferences

/**
 * The types generated Java code refers to: the `bpmn-to-code-runtime` types, `java.util.List`, and the `java.lang`
 * types that are only written out when a generated type shadows them.
 */
internal object JavaRuntimeTypes {

    private const val PACKAGE = "io.miragon.bpmn.runtime"
    private const val JAVA_LANG = "java.lang"

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
        "SequenceFlow",
        "SequenceFlows",
        "SignalEvent",
        "SignalName",
        "TimerEvent",
        "TimerType",
        "VariableDefinitions",
        "VariableName",
    )

    private val QUALIFIED_NAMES = RUNTIME_TYPES.associateWith { "$PACKAGE.$it" } + mapOf(
        "List" to "java.util.List",
        "String" to "$JAVA_LANG.String",
        "Override" to "$JAVA_LANG.Override",
    )

    fun references(generatedTypeNames: Set<String>): TypeReferences = TypeReferences(qualifiedNames = QUALIFIED_NAMES, implicitPackages = setOf(JAVA_LANG), shadowingNames = generatedTypeNames)
}
