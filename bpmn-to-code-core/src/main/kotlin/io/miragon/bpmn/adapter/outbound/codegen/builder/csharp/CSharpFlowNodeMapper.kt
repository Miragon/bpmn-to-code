package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.Timer
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.Variable
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Turns a node of the flow graph into the C# class the template renders, a sealed singleton reached through a property
 * of `FlowNodes`, e.g. (`Runtime` standing for `global::Miragon.BpmnToCode.Runtime`)
 * ```
 * public static ServiceTaskSendContractNode ServiceTaskSendContract => ServiceTaskSendContractNode.Instance;
 *
 * public sealed class ServiceTaskSendContractNode : Runtime.IFlowNode,
 *     Runtime.IHasSuccessors<ServiceTaskSendContractNode.Successors>, Runtime.IHasJobType, Runtime.IHasVariables
 * {
 *     internal static readonly ServiceTaskSendContractNode Instance = new();
 *     public string JobType { get; } = ServiceTasks.SendContractDelegate;
 *     public NodeVariables Variables { get; } = new();
 *     public Successors Next { get; } = new();
 *     …
 * }
 * ```
 * `Next` and `Start` carry the runtime's navigation interfaces, which a `ProcessPath` walks along.
 */
internal class CSharpFlowNodeMapper(private val names: CSharpNodeNames) {

    fun map(node: FlowGraphNode): FlowNode {
        val facets = node.facets
        val name = names.of(node.objectName)
        return FlowNode(
            property = name.property,
            type = name.type,
            interfaces = interfacesOf(node, name.type),
            elementId = stringLiteral(node.id),
            elementType = pascalCase(node.elementType),
            eventType = node.eventType?.let { pascalCase(it) },
            name = nullableStringLiteral(node.name),
            jobType = facets.jobType?.let { CSharpSharedReferences.constantOrLiteral(SERVICE_TASKS, it) },
            calledProcess = facets.calledProcessId?.let { stringLiteral(it) },
            timer = facets.timer?.let { timerOf(it) },
            message = facets.message?.let { CSharpSharedReferences.constantOrLiteral(MESSAGES, it) },
            signal = facets.signal?.let { CSharpSharedReferences.constantOrLiteral(SIGNALS, it) },
            error = facets.error?.let { CSharpSharedReferences.nameAndCode(ERRORS, it) },
            escalation = facets.escalation?.let { CSharpSharedReferences.nameAndCode(ESCALATIONS, it) },
            attachedTo = facets.attachedTo?.let { names.of(it.objectName) },
            interrupting = facets.isInterrupting,
            variables = facets.variables.map { variableOf(it) },
            inputs = facets.inputs.map { mappingOf(it) },
            outputs = facets.outputs.map { mappingOf(it) },
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { names.of(it.objectName) },
        )
    }

    private fun interfacesOf(node: FlowGraphNode, typeName: String): List<String> {
        val facetInterfaces = node.facets.facetInterfaces.map { CSharpRuntimeTypes.facetInterface(it.typeName) }
        return listOf(nodeInterfaceOf(node)) + navigationInterfacesOf(node, typeName) + facetInterfaces
    }

    private fun nodeInterfaceOf(node: FlowGraphNode): String = when {
        node.isBoundaryEvent -> CSharpRuntimeTypes.BOUNDARY_EVENT
        node.eventType != null -> CSharpRuntimeTypes.EVENT
        else -> CSharpRuntimeTypes.FLOW_NODE
    }

    /**
     * `IHasSuccessors<…Node.Successors>` for `Next`, `IFlowScope<…Node.Interior>` for `Start`, and
     * `ISubProcess<…Node.Interior, …Node.Successors>` for a subprocess that has both.
     */
    private fun navigationInterfacesOf(node: FlowGraphNode, typeName: String): List<String> {
        val successors = "$typeName.Successors"
        val interior = "$typeName.Interior"
        val hasSuccessors = node.successors.isNotEmpty()
        val hasInterior = node.interiorStarts.isNotEmpty()
        return when {
            hasSuccessors && hasInterior -> listOf("${CSharpRuntimeTypes.SUB_PROCESS}<$interior, $successors>")
            hasSuccessors -> listOf("${CSharpRuntimeTypes.HAS_SUCCESSORS}<$successors>")
            hasInterior -> listOf("${CSharpRuntimeTypes.FLOW_SCOPE}<$interior>")
            else -> emptyList()
        }
    }

    private fun timerOf(timer: TimerFacet): Timer = Timer(
        type = pascalCase(timer.type.name),
        expression = stringLiteral(timer.expression),
    )

    private fun variableOf(variable: VariableFacet): Variable = Variable(
        name = variablePropertyName(variable),
        subtype = variable.subtype.simpleName,
        nameConstant = CSharpSharedReferences.reference(PROCESS_VARIABLES, variable.constantName),
    )

    /**
     * A variable called `all`, `inputs` or `outputs` would hide the member of that name it inherits from
     * `VariableDefinitions`, and one called `processVariables` the class its name constant lives in: `all` becomes
     * `All_`.
     */
    private fun variablePropertyName(variable: VariableFacet): String {
        val propertyName = pascalCase(variable.rawName)
        val hiddenNames = CSharpRuntimeTypes.VARIABLE_DEFINITIONS_MEMBERS + PROCESS_VARIABLES.typeName
        if (propertyName !in hiddenNames) return propertyName
        return propertyName + "_"
    }

    private fun mappingOf(mapping: MappingFacet): Mapping = Mapping(
        name = pascalCase(mapping.target),
        target = stringLiteral(mapping.target),
        source = nullableStringLiteral(mapping.source),
        sourceExpression = nullableStringLiteral(mapping.sourceExpression),
    )

    private fun successorOf(successor: SuccessorEdge): Successor = Successor(
        target = names.of(successor.target.objectName),
        type = successorTypeOf(successor),
        flows = sequenceFlowsOf(successor).map { flowOf(it) },
    )

    private fun successorTypeOf(successor: SuccessorEdge): String = when (successor) {
        is SuccessorEdge.ViaSequenceFlows -> "SequenceFlows"
        is SuccessorEdge.AttachedBoundaryEvent -> "AttachedBoundaryEvent"
        is SuccessorEdge.AssociatedCompensationHandler -> "AssociatedCompensationHandler"
    }

    private fun sequenceFlowsOf(successor: SuccessorEdge): List<SequenceFlowEdge> = when (successor) {
        is SuccessorEdge.ViaSequenceFlows -> successor.flows
        is SuccessorEdge.AttachedBoundaryEvent -> emptyList()
        is SuccessorEdge.AssociatedCompensationHandler -> emptyList()
    }

    private fun flowOf(flow: SequenceFlowEdge): Flow = Flow(
        id = stringLiteral(flow.id),
        name = flow.name?.let { stringLiteral(it) },
        conditionExpression = flow.conditionExpression?.let { stringLiteral(it) },
        defaultFlow = flow.isDefault,
    )
}
