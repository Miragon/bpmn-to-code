package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.InteriorStart
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.Timer
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaProcessApiModel.Variable
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaSyntax.stringLiteral
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
 * Turns a node of the flow graph into the Java class the template renders, e.g.
 * ```
 * public static final class ServiceTaskSendContract extends AbstractFlowNode
 *     implements HasSuccessors<ServiceTaskSendContract.Next>, HasJobType, HasVariables {
 *   public static final ServiceTaskSendContract INSTANCE = new ServiceTaskSendContract();
 *   @Override public String getJobType() { return ServiceTasks.SEND_CONTRACT_DELEGATE; }
 *   @Override public Variables getVariables() { return VARIABLES; }
 *   @Override public Next getNext() { return new Next(); }
 *   public static final class Variables extends VariableDefinitions { … }
 *   public static final class Next { … }
 * }
 * ```
 */
internal class JavaFlowNodeMapper(private val types: TypeReferences) {

    private val sharedReferences = JvmSharedReferences(JavaSyntax)
    private val supertypes = JvmSupertypes(types, JavaSyntax)

    fun map(node: FlowGraphNode): FlowNode {
        val facets = node.facets
        return FlowNode(
            className = identifier(node.objectName),
            accessorName = identifier(node.propertyName),
            interfaces = supertypes.of(node),
            elementId = stringLiteral(node.id),
            elementType = node.elementType,
            name = node.name?.let { stringLiteral(it) },
            eventType = node.eventType,
            jobType = facets.jobType?.let { sharedReferences.constantOrLiteral(SERVICE_TASKS, it) },
            calledProcess = facets.calledProcessId?.let { stringLiteral(it) },
            timer = facets.timer?.let { Timer(type = it.type.name, expression = stringLiteral(it.expression)) },
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
        className = identifier(start.objectName),
    )

    private fun variableOf(variable: VariableFacet): Variable = Variable(
        name = identifier(variable.constantName),
        subtype = variable.subtype.simpleName,
        nameConstant = sharedReferences.reference(PROCESS_VARIABLES, variable.constantName),
    )

    private fun mappingOf(mapping: MappingFacet): Mapping = Mapping(
        name = identifier(mapping.constantName),
        target = stringLiteral(mapping.target),
        source = nullableStringLiteral(mapping.source),
        sourceExpression = nullableStringLiteral(mapping.sourceExpression),
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
        name = nullableStringLiteral(flow.name),
        conditionExpression = nullableStringLiteral(flow.conditionExpression),
        defaultFlow = flow.isDefault,
        onlyDefaults = flow.hasOnlyDefaults(),
    )
}
