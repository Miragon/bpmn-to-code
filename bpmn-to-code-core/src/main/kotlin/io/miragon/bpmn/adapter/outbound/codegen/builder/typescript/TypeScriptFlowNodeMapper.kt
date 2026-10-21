package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.Flow
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.FlowNode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.InteriorStart
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.Mapping
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.Successor
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.Timer
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptProcessApiModel.Variable
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.identifier
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.SharedNameAndCode

/**
 * Turns a node of the flow graph into the exported singleton class the template renders, e.g.
 * ```
 * export class ServiceTaskSendContract implements Runtime.FlowNode, Runtime.HasSuccessors, Runtime.HasJobType {
 *   static readonly INSTANCE = new ServiceTaskSendContract();
 *   readonly jobType = ServiceTasks.SEND_CONTRACT_DELEGATE;
 *   readonly next = {
 *     get endEventContractSent(): Runtime.SequenceFlows<EndEventContractSent> { … },
 *   };
 * }
 * ```
 * Everything that leads to another node is a getter, so no class touches another one while the module loads. Runtime
 * names and shared constants are resolved through [file], which imports them.
 */
internal class TypeScriptFlowNodeMapper(private val file: TypeScriptSourceFile) {

    fun map(node: FlowGraphNode): FlowNode {
        val facets = node.facets
        return FlowNode(
            className = identifier(node.objectName),
            interfaces = interfacesOf(node),
            elementId = stringLiteral(node.id),
            elementType = stringLiteral(node.elementType),
            eventType = node.eventType?.let { stringLiteral(it) },
            name = nullableStringLiteral(node.name),
            jobType = facets.jobType?.let { constantOrLiteral(SERVICE_TASKS, it) },
            calledProcess = facets.calledProcessId?.let { stringLiteral(it) },
            timer = facets.timer?.let { timerOf(it) },
            message = facets.message?.let { constantOrLiteral(MESSAGES, it) },
            signal = facets.signal?.let { constantOrLiteral(SIGNALS, it) },
            error = facets.error?.let { sharedNameAndCode(ERRORS, it) },
            escalation = facets.escalation?.let { sharedNameAndCode(ESCALATIONS, it) },
            interrupting = facets.isInterrupting,
            throwsCompensation = facets.throwsCompensation,
            attachedTo = facets.attachedTo?.let { identifier(it.objectName) },
            variables = facets.variables.map { variableOf(it) },
            inputs = facets.inputs.map { mappingOf(it) },
            outputs = facets.outputs.map { mappingOf(it) },
            successors = node.successors.map { successorOf(it) },
            interiorStarts = node.interiorStarts.map { interiorStartOf(it) },
        )
    }

    /**
     * e.g. `Runtime.BoundaryEvent<ServiceTaskValidateApplication>, Runtime.HasSuccessors, Runtime.ErrorEvent`;
     * `HasSuccessors` and `FlowScope` mark what a process path can walk along.
     */
    private fun interfacesOf(node: FlowGraphNode): List<String> = buildList {
        add(nodeInterfaceOf(node))
        if (node.successors.isNotEmpty()) {
            add(file.runtimeType("HasSuccessors"))
        }
        if (node.interiorStarts.isNotEmpty()) {
            add(file.runtimeType("FlowScope"))
        }
        addAll(node.facets.facetInterfaces.map { file.runtimeType(it.typeName) })
    }

    private fun nodeInterfaceOf(node: FlowGraphNode): String {
        val host = node.facets.attachedTo
        if (node.isBoundaryEvent && host != null) {
            return "${file.runtimeType("BoundaryEvent")}<${identifier(host.objectName)}>"
        }
        if (node.eventType != null) {
            return file.runtimeType("Event")
        }
        return file.runtimeType("FlowNode")
    }

    private fun timerOf(timer: TimerFacet): Timer = Timer(
        type = stringLiteral(timer.type.name),
        value = stringLiteral(timer.expression),
    )

    private fun variableOf(variable: VariableFacet): Variable = Variable(
        key = identifier(variable.constantName),
        subtype = variable.subtype.simpleName,
        nameConstant = file.sharedReference(PROCESS_VARIABLES, variable.constantName),
    )

    private fun mappingOf(mapping: MappingFacet): Mapping = Mapping(
        key = identifier(mapping.constantName),
        target = stringLiteral(mapping.target),
        source = nullableStringLiteral(mapping.source),
        sourceExpression = nullableStringLiteral(mapping.sourceExpression),
    )

    private fun successorOf(successor: SuccessorEdge): Successor = Successor(
        key = identifier(successor.target.propertyName),
        target = identifier(successor.target.objectName),
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

    private fun interiorStartOf(start: FlowEdge): InteriorStart = InteriorStart(
        key = identifier(start.propertyName),
        className = identifier(start.objectName),
    )

    /**
     * e.g. `Messages.MIRAVELO_CONTRACT_SIGNED`, or `"miravelo.contractSigned"` when no shared constant holds the value
     */
    private fun constantOrLiteral(type: SharedDefinitionType, sharedValue: SharedValue<String>): String {
        val constant = sharedValue.constant ?: return stringLiteral(sharedValue.value)
        return file.sharedReference(type, constant.name)
    }

    private fun sharedNameAndCode(
        type: SharedDefinitionType,
        sharedValue: SharedValue<Pair<String, String>>,
    ): SharedNameAndCode {
        val (name, code) = sharedValue.value
        return SharedNameAndCode(
            reference = sharedValue.constant?.let { file.sharedReference(type, it.name) },
            name = stringLiteral(name),
            code = stringLiteral(code),
        )
    }
}
