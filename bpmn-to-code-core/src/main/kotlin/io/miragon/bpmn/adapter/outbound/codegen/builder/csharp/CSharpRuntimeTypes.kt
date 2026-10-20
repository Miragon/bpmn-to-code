package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.CSharpCodeFormat.pascalCase

/**
 * Names of the C# counterpart of `bpmn-to-code-runtime`. The types themselves are the fixed text
 * `templates/csharp/runtime-types.cs`, emitted verbatim into every generated file as a nested `Runtime` class of the
 * process API. Nesting keeps two generated files in one assembly from clashing and spares consumers a package: they
 * reference `MyProcessApi.Runtime.ElementId`.
 */
internal object CSharpRuntimeTypes {

    const val CLASS_NAME = "Runtime"

    const val BOUNDARY_EVENT = "$CLASS_NAME.IBoundaryEvent"
    const val BPMN_ELEMENT_TYPE = "$CLASS_NAME.BpmnElementType"
    const val BPMN_ENGINE = "$CLASS_NAME.BpmnEngine"
    const val BPMN_ERROR_DEFINITION = "$CLASS_NAME.BpmnErrorDefinition"
    const val BPMN_ESCALATION_DEFINITION = "$CLASS_NAME.BpmnEscalationDefinition"
    const val BPMN_EVENT_TYPE = "$CLASS_NAME.BpmnEventType"
    const val BPMN_TIMER = "$CLASS_NAME.BpmnTimer"
    const val ELEMENT_ID = "$CLASS_NAME.ElementId"
    const val EVENT = "$CLASS_NAME.IEvent"
    const val FLOW_NODE = "$CLASS_NAME.IFlowNode"
    const val INPUT_OUTPUT_MAPPING = "$CLASS_NAME.InputOutputMapping"
    const val MESSAGE_NAME = "$CLASS_NAME.MessageName"
    const val PROCESS_ID = "$CLASS_NAME.ProcessId"
    const val ATTACHED_BOUNDARY_EVENT = "$CLASS_NAME.AttachedBoundaryEvent"
    const val ASSOCIATED_COMPENSATION_HANDLER = "$CLASS_NAME.AssociatedCompensationHandler"
    const val SEQUENCE_FLOW = "$CLASS_NAME.SequenceFlow"
    const val SEQUENCE_FLOWS = "$CLASS_NAME.SequenceFlows"
    const val SIGNAL_NAME = "$CLASS_NAME.SignalName"
    const val TIMER_TYPE = "$CLASS_NAME.TimerType"
    const val VARIABLE_DEFINITIONS = "$CLASS_NAME.VariableDefinitions"
    const val VARIABLE_NAME = "$CLASS_NAME.VariableName"
    const val HAS_VARIABLES = "$CLASS_NAME.IHasVariables"

    val VARIABLE_DEFINITIONS_MEMBERS = setOf("All", "Inputs", "Outputs")

    fun facetInterface(typeName: String): String = "$CLASS_NAME.I$typeName"

    fun enumMember(enumType: String, constantName: String): String = "$enumType.${pascalCase(constantName)}"

    private const val SOURCE_RESOURCE = "/io/miragon/bpmn/codegen/templates/csharp/runtime-types.cs"

    val SOURCE: String = requireNotNull(CSharpRuntimeTypes::class.java.getResource(SOURCE_RESOURCE)).readText().trimIndent()
}
