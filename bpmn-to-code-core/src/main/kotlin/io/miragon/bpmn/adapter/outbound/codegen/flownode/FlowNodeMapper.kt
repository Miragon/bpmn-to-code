package io.miragon.bpmn.adapter.outbound.codegen.flownode

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Timer
import io.miragon.bpmn.adapter.outbound.codegen.flownode.FlowNodeModel.Variable

/**
 * Turns a node of the flow graph into the [FlowNodeModel] every language renders. What differs per language is how a
 * value or a name is written, which [syntax] decides.
 */
internal class FlowNodeMapper(private val syntax: FlowNodeSyntax) {

    fun map(node: FlowGraphNode): FlowNodeModel {
        val facets = node.facets
        val name = syntax.nodeName(FlowEdge(propertyName = node.propertyName, objectName = node.objectName))
        return FlowNodeModel(
            type = name.type,
            property = name.property,
            interfaces = syntax.interfacesOf(node),
            elementId = syntax.literal(node.id),
            elementType = syntax.enumMember(node.elementType),
            eventType = node.eventType?.let { syntax.enumMember(it) },
            name = node.name?.let { syntax.literal(it) },
            jobType = facets.jobType?.let { constantOrLiteral(SERVICE_TASKS, it) },
            calledProcess = facets.calledProcessId?.let { syntax.literal(it) },
            timer = facets.timer?.let { Timer(type = syntax.enumMember(it.type.name), expression = syntax.literal(it.expression)) },
            message = facets.message?.let { sharedName(MESSAGES, it) },
            signal = facets.signal?.let { sharedName(SIGNALS, it) },
            error = facets.error?.let { syntax.sharedNameAndCode(ERRORS, it) },
            escalation = facets.escalation?.let { syntax.sharedNameAndCode(ESCALATIONS, it) },
            attachedTo = facets.attachedTo?.let { syntax.nodeName(it) },
            interrupting = facets.isInterrupting,
            throwsCompensation = facets.throwsCompensation,
            variables = facets.variables.map { variableOf(it) },
            inputs = facets.inputs.map { mappingOf(it) },
            outputs = facets.outputs.map { mappingOf(it) },
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { syntax.nodeName(it) },
        )
    }

    private fun constantOrLiteral(type: SharedDefinitionType, sharedValue: SharedValue<String>): String {
        val constant = sharedValue.constant ?: return syntax.literal(sharedValue.value)
        return syntax.sharedReference(type, constant.name)
    }

    private fun sharedName(type: SharedDefinitionType, sharedValue: SharedValue<String>): SharedName = SharedName(
        reference = sharedValue.constant?.let { syntax.sharedReference(type, it.name) },
        literal = syntax.literal(sharedValue.value),
    )

    private fun variableOf(variable: VariableFacet): Variable = Variable(
        name = syntax.variableName(variable),
        subtype = variable.subtype.simpleName,
        nameConstant = syntax.sharedReference(PROCESS_VARIABLES, variable.constantName),
    )

    private fun mappingOf(mapping: MappingFacet): Mapping = Mapping(
        name = syntax.mappingName(mapping),
        target = syntax.literal(mapping.target),
        source = mapping.source?.let { syntax.literal(it) },
        sourceExpression = mapping.sourceExpression?.let { syntax.literal(it) },
    )

    private fun successorOf(successor: SuccessorEdge): Successor = Successor(
        target = syntax.nodeName(successor.target),
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
        id = syntax.literal(flow.id),
        name = flow.name?.let { syntax.literal(it) },
        conditionExpression = flow.conditionExpression?.let { syntax.literal(it) },
        defaultFlow = flow.isDefault,
    )
}
