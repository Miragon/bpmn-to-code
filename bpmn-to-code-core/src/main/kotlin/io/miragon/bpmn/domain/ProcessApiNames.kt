package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * How an element is named in the generated Process API, and which names the API declares itself. Code generation names
 * elements with [elementName] and the validation rules check that same name, so a model the rules accept generates.
 *
 * Every element becomes a nested type inside `FlowNodes`, next to the holders (`Next`, `Start`, …), the
 * `all` / `all()` / `All` enumeration, and refers to the shared definitions (`ServiceTasks`, `Messages`, …) and runtime
 * types by their simple name. An element named like one of those would shadow it in at least one target language; an
 * element whose accessor is named like a `java.lang.Object` method breaks the Java output the same way. In C#, the
 * members of a node (`Id`, `Name`, `Message`, `Timer`, …) are PascalCase too and shadow an element of that name
 * wherever the node refers to it.
 */
object ProcessApiNames {

    /**
     * The holders a Process API declares: `FlowNodes`, the `All` enumeration, and a node's `Next`, `Start`,
     * `Variables`, `Inputs` and `Outputs`.
     */
    val HOLDER_NAMES: Set<String> = setOf("FlowNodes", "All", "Next", "Start", "Variables", "Inputs", "Outputs")

    val RESERVED_TYPE_NAMES: Set<String> = HOLDER_NAMES + setOf(
        "Instance",
        "Runtime",
        "Successors",
        "Interior",
        "NodeVariables",
        "InputMappings",
        "OutputMappings",
        "Id",
        "Name",
        "ElementType",
        "EventType",
        "AttachedTo",
        "IsInterrupting",
        "JobType",
        "CalledProcess",
        "Message",
        "Signal",
        "Timer",
        "Error",
        "Escalation",
        "ElementId",
        "ProcessId",
        "MessageName",
        "SignalName",
        "VariableName",
        "BpmnTimer",
        "BpmnErrorDefinition",
        "BpmnEscalationDefinition",
        "InputOutputMapping",
        "SequenceFlow",
        "SequenceFlows",
        "AttachedBoundaryEvent",
        "AssociatedCompensationHandler",
        "ISuccessor",
        "FlowNode",
        "AbstractFlowNode",
        "HasSuccessors",
        "BoundaryEvent",
        "IBoundaryEvent",
        "FlowScope",
        "HasJobType",
        "HasVariables",
        "VariableDefinitions",
        "RegisteredVariableDefinitions",
        "CallActivity",
        "TimerEvent",
        "HasMessage",
        "SignalEvent",
        "ErrorEvent",
        "EscalationEvent",
        "CompensationThrowEvent",
        "IHasJobType",
        "IHasVariables",
        "ICallActivity",
        "ITimerEvent",
        "IHasMessage",
        "ISignalEvent",
        "IErrorEvent",
        "IEscalationEvent",
        "ICompensationThrowEvent",
        "BpmnEngine",
        "IFlowNode",
        "ISequenceFlow",
        "Messages",
        "Errors",
        "Signals",
        "Escalations",
        "ServiceTasks",
        "ProcessVariables",
    )

    val RESERVED_ACCESSOR_NAMES: Set<String> = setOf(
        "hashCode",
        "toString",
        "getClass",
        "notify",
        "notifyAll",
        "wait",
        "equals",
    )

    /**
     * e.g. `serviceTask_sendContract` is `ServiceTaskSendContract`
     */
    fun elementName(elementId: String): String = elementId.toCamelCase()

    fun isReserved(elementName: String): Boolean {
        val accessorName = elementName.replaceFirstChar { it.lowercaseChar() }
        return elementName in RESERVED_TYPE_NAMES || accessorName in RESERVED_ACCESSOR_NAMES
    }
}
