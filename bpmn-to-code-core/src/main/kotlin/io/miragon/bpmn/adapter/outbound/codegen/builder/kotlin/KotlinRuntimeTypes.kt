package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName

/**
 * The `bpmn-to-code-runtime` types the generated Kotlin code refers to.
 */
internal object KotlinRuntimeTypes {

    private const val PACKAGE = "io.miragon.bpmn.runtime"

    val ABSTRACT_FLOW_NODE = ClassName(PACKAGE, "AbstractFlowNode")
    val BOUNDARY_EVENT = ClassName(PACKAGE, "BoundaryEvent")
    val BPMN_ELEMENT_TYPE = ClassName(PACKAGE, "BpmnElementType")
    val BPMN_ENGINE = ClassName(PACKAGE, "BpmnEngine")
    val BPMN_ERROR_DEFINITION = ClassName(PACKAGE, "BpmnErrorDefinition")
    val BPMN_ESCALATION_DEFINITION = ClassName(PACKAGE, "BpmnEscalationDefinition")
    val BPMN_EVENT_TYPE = ClassName(PACKAGE, "BpmnEventType")
    val BPMN_TIMER = ClassName(PACKAGE, "BpmnTimer")
    val ELEMENT_ID = ClassName(PACKAGE, "ElementId")
    val EVENT = ClassName(PACKAGE, "Event")
    val FLOW_NODE = ClassName(PACKAGE, "FlowNode")
    val FLOW_SCOPE = ClassName(PACKAGE, "FlowScope")
    val HAS_OUTGOING_FLOWS = ClassName(PACKAGE, "HasOutgoingFlows")
    val HAS_SUCCESSORS = ClassName(PACKAGE, "HasSuccessors")
    val INPUT_OUTPUT_MAPPING = ClassName(PACKAGE, "InputOutputMapping")
    val MESSAGE_NAME = ClassName(PACKAGE, "MessageName")
    val PROCESS_ID = ClassName(PACKAGE, "ProcessId")
    val SEQUENCE_FLOW = ClassName(PACKAGE, "SequenceFlow")
    val SIGNAL_NAME = ClassName(PACKAGE, "SignalName")
    val TIMER_TYPE = ClassName(PACKAGE, "TimerType")
    val VARIABLE_NAME = ClassName(PACKAGE, "VariableName")
}
