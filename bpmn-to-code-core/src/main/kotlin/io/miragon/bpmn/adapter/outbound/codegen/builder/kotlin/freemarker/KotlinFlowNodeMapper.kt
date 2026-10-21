package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.InteriorStart
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.Timer
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinProcessApiModel.Variable
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.freemarker.KotlinSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.JvmSharedReferences
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.JvmSupertypes
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * Turns a node of the flow graph into the Kotlin object the template renders, e.g.
 * ```
 * object ServiceTaskSendContract : AbstractFlowNode(…),
 *   HasSuccessors<ServiceTaskSendContract.Next>, HasJobType, HasVariables {
 *   override val jobType: String = ServiceTasks.SEND_CONTRACT_DELEGATE
 *   override val variables: Variables = Variables
 *   override val next: Next = Next
 *   object Variables : RegisteredVariableDefinitions() { … }
 *   object Next { … }
 * }
 * ```
 * `Variables` registers each variable it declares, so its `all` needs no generated list. Whatever points at another
 * node is a getter, so object initialisation never touches another node.
 */
internal class KotlinFlowNodeMapper(private val types: TypeReferences) {

    private val sharedReferences = JvmSharedReferences(KotlinSyntax)
    private val supertypes = JvmSupertypes(types, KotlinSyntax)

    fun map(node: FlowGraphNode): FlowNode {
        val facets = node.facets
        return FlowNode(
            objectName = identifier(node.objectName),
            elementType = node.elementType,
            name = node.name?.let { stringLiteral(it) },
            interfaces = supertypes.of(node),
            eventType = node.eventType,
            elementId = stringLiteral(node.id),
            jobType = facets.jobType?.let { sharedReferences.constantOrLiteral(SERVICE_TASKS, it) },
            calledProcess = facets.calledProcessId?.let { stringLiteral(it) },
            timer = facets.timer?.let { Timer(type = it.type.name, value = stringLiteral(it.expression)) },
            message = facets.message?.let { sharedReferences.sharedName(MESSAGES, it) },
            signal = facets.signal?.let { sharedReferences.sharedName(SIGNALS, it) },
            error = facets.error?.let { sharedReferences.sharedNameAndCode(ERRORS, it) },
            escalation = facets.escalation?.let { sharedReferences.sharedNameAndCode(ESCALATIONS, it) },
            attachedTo = facets.attachedTo?.let { identifier(it.objectName) },
            interrupting = facets.isInterrupting,
            variables = facets.variables.map { variableOf(it) },
            inputs = facets.inputs.map { mappingOf(it) },
            outputs = facets.outputs.map { mappingOf(it) },
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { interiorStartOf(it) },
        )
    }

    private fun interiorStartOf(start: FlowEdge): InteriorStart = InteriorStart(
        name = identifier(start.propertyName),
        objectName = identifier(start.objectName),
    )

    private fun variableOf(variable: VariableFacet): Variable = Variable(
        name = identifier(variable.constantName),
        subtype = variable.subtype.simpleName,
        nameConstant = sharedReferences.reference(PROCESS_VARIABLES, variable.constantName),
    )

    private fun mappingOf(mapping: MappingFacet): Mapping = Mapping(
        name = identifier(mapping.constantName),
        target = stringLiteral(mapping.target),
        source = mapping.source?.let { stringLiteral(it) },
        sourceExpression = mapping.sourceExpression?.let { stringLiteral(it) },
    )

    private fun successorOf(successor: SuccessorEdge): Successor = Successor(
        name = identifier(successor.target.propertyName),
        type = successorTypeOf(successor),
        target = identifier(successor.target.objectName),
        flows = sequenceFlowsOf(successor).map { flowOf(it) },
    )

    private fun successorTypeOf(successor: SuccessorEdge): String = when (successor) {
        is SuccessorEdge.ViaSequenceFlows -> types.of("SequenceFlows")
        is SuccessorEdge.AttachedBoundaryEvent -> types.of("AttachedBoundaryEvent")
        is SuccessorEdge.AssociatedCompensationHandler -> types.of("AssociatedCompensationHandler")
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
