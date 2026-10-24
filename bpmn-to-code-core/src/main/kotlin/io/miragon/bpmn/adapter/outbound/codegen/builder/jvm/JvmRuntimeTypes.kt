package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

/**
 * The `bpmn-to-code-runtime` types generated Kotlin and Java code refers to, by simple name: `SequenceFlows` is
 * `io.miragon.bpmn.runtime.SequenceFlows`.
 */
internal object JvmRuntimeTypes {

    private const val PACKAGE = "io.miragon.bpmn.runtime"

    private val SIMPLE_NAMES = listOf(
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
        "VariableName",
    )

    fun qualifiedNames(languageSpecificNames: List<String>): Map<String, String> {
        val simpleNames = SIMPLE_NAMES + languageSpecificNames
        return simpleNames.associateWith { "$PACKAGE.$it" }
    }
}
