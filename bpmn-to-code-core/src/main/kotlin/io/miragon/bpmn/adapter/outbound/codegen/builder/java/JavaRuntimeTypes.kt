package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName

/**
 * The `bpmn-to-code-runtime` types the generated Java code refers to.
 */
internal object JavaRuntimeTypes {

    const val PACKAGE = "io.miragon.bpmn.runtime"

    val ABSTRACT_FLOW_NODE: ClassName = ClassName.get(PACKAGE, "AbstractFlowNode")
    val ATTACHED_BOUNDARY_EVENT: ClassName = ClassName.get(PACKAGE, "AttachedBoundaryEvent")
    val BOUNDARY_EVENT: ClassName = ClassName.get(PACKAGE, "BoundaryEvent")
    val BPMN_ELEMENT_TYPE: ClassName = ClassName.get(PACKAGE, "BpmnElementType")
    val BPMN_ENGINE: ClassName = ClassName.get(PACKAGE, "BpmnEngine")
    val BPMN_ERROR_DEFINITION: ClassName = ClassName.get(PACKAGE, "BpmnErrorDefinition")
    val BPMN_ESCALATION_DEFINITION: ClassName = ClassName.get(PACKAGE, "BpmnEscalationDefinition")
    val BPMN_EVENT_TYPE: ClassName = ClassName.get(PACKAGE, "BpmnEventType")
    val BPMN_TIMER: ClassName = ClassName.get(PACKAGE, "BpmnTimer")
    val ELEMENT_ID: ClassName = ClassName.get(PACKAGE, "ElementId")
    val EVENT: ClassName = ClassName.get(PACKAGE, "Event")
    val FLOW_NODE: ClassName = ClassName.get(PACKAGE, "FlowNode")
    val FLOW_SCOPE: ClassName = ClassName.get(PACKAGE, "FlowScope")
    val HAS_SUCCESSORS: ClassName = ClassName.get(PACKAGE, "HasSuccessors")
    val INPUT_OUTPUT_MAPPING: ClassName = ClassName.get(PACKAGE, "InputOutputMapping")
    val MESSAGE_NAME: ClassName = ClassName.get(PACKAGE, "MessageName")
    val PROCESS_ID: ClassName = ClassName.get(PACKAGE, "ProcessId")
    val SEQUENCE_FLOW: ClassName = ClassName.get(PACKAGE, "SequenceFlow")
    val SEQUENCE_FLOWS: ClassName = ClassName.get(PACKAGE, "SequenceFlows")
    val SIGNAL_NAME: ClassName = ClassName.get(PACKAGE, "SignalName")
    val TIMER_TYPE: ClassName = ClassName.get(PACKAGE, "TimerType")
    val VARIABLE_NAME: ClassName = ClassName.get(PACKAGE, "VariableName")
}
