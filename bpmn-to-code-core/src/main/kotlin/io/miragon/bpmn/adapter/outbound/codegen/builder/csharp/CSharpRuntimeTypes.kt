package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase

/**
 * The types of `Miragon.BpmnToCode.Runtime`, the C# counterpart of `bpmn-to-code-runtime` (in `packages/nuget`),
 * which the generated code refers to. They are qualified with `global::`, so no type or namespace of the consumer
 * can shadow them.
 */
internal object CSharpRuntimeTypes {

    const val NAMESPACE = "global::Miragon.BpmnToCode.Runtime"

    const val BOUNDARY_EVENT = "$NAMESPACE.IBoundaryEvent"
    const val BPMN_ELEMENT_TYPE = "$NAMESPACE.BpmnElementType"
    const val BPMN_ENGINE = "$NAMESPACE.BpmnEngine"
    const val BPMN_ERROR_DEFINITION = "$NAMESPACE.BpmnErrorDefinition"
    const val BPMN_ESCALATION_DEFINITION = "$NAMESPACE.BpmnEscalationDefinition"
    const val BPMN_EVENT_TYPE = "$NAMESPACE.BpmnEventType"
    const val BPMN_TIMER = "$NAMESPACE.BpmnTimer"
    const val ELEMENT_ID = "$NAMESPACE.ElementId"
    const val EVENT = "$NAMESPACE.IEvent"
    const val FLOW_NODE = "$NAMESPACE.IFlowNode"
    const val INPUT_OUTPUT_MAPPING = "$NAMESPACE.InputOutputMapping"
    const val MESSAGE_NAME = "$NAMESPACE.MessageName"
    const val PROCESS_ID = "$NAMESPACE.ProcessId"
    const val ATTACHED_BOUNDARY_EVENT = "$NAMESPACE.AttachedBoundaryEvent"
    const val ASSOCIATED_COMPENSATION_HANDLER = "$NAMESPACE.AssociatedCompensationHandler"
    const val SEQUENCE_FLOW = "$NAMESPACE.SequenceFlow"
    const val SEQUENCE_FLOWS = "$NAMESPACE.SequenceFlows"
    const val SIGNAL_NAME = "$NAMESPACE.SignalName"
    const val TIMER_TYPE = "$NAMESPACE.TimerType"
    const val VARIABLE_DEFINITIONS = "$NAMESPACE.VariableDefinitions"
    const val VARIABLE_NAME = "$NAMESPACE.VariableName"
    const val HAS_VARIABLES = "$NAMESPACE.IHasVariables"
    const val HAS_SUCCESSORS = "$NAMESPACE.IHasSuccessors"
    const val FLOW_SCOPE = "$NAMESPACE.IFlowScope"
    const val SUB_PROCESS = "$NAMESPACE.ISubProcess"

    val VARIABLE_DEFINITIONS_MEMBERS = setOf("All", "Inputs", "Outputs")

    fun facetInterface(typeName: String): String = "$NAMESPACE.I$typeName"

    fun enumMember(enumType: String, constantName: String): String = "$enumType.${pascalCase(constantName)}"
}
