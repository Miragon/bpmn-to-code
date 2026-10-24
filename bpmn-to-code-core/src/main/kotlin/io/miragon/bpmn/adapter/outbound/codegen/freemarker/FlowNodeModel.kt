package io.miragon.bpmn.adapter.outbound.codegen.freemarker

/**
 * A flow node as every `process-api.ftl` renders it. Strings are literals, identifiers or references written in the
 * target language by a [FlowNodeSyntax], and the template writes the code around them. A facet is `null`, or empty,
 * when the node does not have it; an optional literal is `null` when the model declares no value.
 *
 * @property type the node's own type, e.g. `ServiceTaskSendContract`, `ServiceTaskSendContractNode` in C#
 * @property property the member the node is reached through, e.g. `serviceTaskSendContract`, `ServiceTaskSendContract`
 * in C#
 * @property interfaces e.g. `HasSuccessors<ServiceTaskSendContract.Next>`, `HasJobType`, `HasVariables`
 * @property elementType a `BpmnElementType` member, e.g. `SERVICE_TASK`, `ServiceTask` in C#, `"SERVICE_TASK"` in
 * TypeScript
 * @property jobType e.g. `ServiceTasks.SEND_CONTRACT_DELEGATE`, or a literal when no shared constant holds it
 * @property attachedTo the host of a boundary event, e.g. `ServiceTaskValidateApplication`
 * @property interiorStarts the start elements of a subprocess, e.g. `StartEventAddressChanged`
 */
internal data class FlowNodeModel(
    val type: String,
    val property: String,
    val interfaces: List<String>,
    val elementId: String,
    val elementType: String,
    val eventType: String?,
    val name: String?,
    val jobType: String?,
    val calledProcess: String?,
    val timer: Timer?,
    val message: SharedName?,
    val signal: SharedName?,
    val error: SharedNameAndCode?,
    val escalation: SharedNameAndCode?,
    val attachedTo: NodeName?,
    val interrupting: Boolean?,
    val throwsCompensation: Boolean,
    val variables: List<Variable>,
    val inputs: List<Mapping>,
    val outputs: List<Mapping>,
    val successors: List<Successor>,
    val interiorStarts: List<NodeName>,
) {

    /**
     * Another node, as its [FlowNodeModel.type] and [FlowNodeModel.property].
     */
    data class NodeName(val type: String, val property: String)

    /**
     * @property type a `TimerType` member, e.g. `DURATION`
     * @property expression e.g. `"P14D"`
     */
    data class Timer(val type: String, val expression: String)

    /**
     * Rendered as `val APPLICATION_ID: VariableName.Input = input(ProcessVariables.APPLICATION_ID)` in Kotlin.
     *
     * @property subtype a `VariableName` subtype, e.g. `Input`
     */
    data class Variable(val name: String, val subtype: String, val nameConstant: String)

    /**
     * e.g. [name] `APPLICATION_ID`, [target] `"applicationId"`, [source] `null`, [sourceExpression] `"${applicationId}"`
     */
    data class Mapping(val name: String, val target: String, val source: String?, val sourceExpression: String?)

    /**
     * @property type the runtime type leading to [target], e.g. `SequenceFlows`, or `AttachedBoundaryEvent` for a
     * successor reached without a flow
     * @property flows the sequence flows leading to [target]; empty when it is reached without one
     */
    data class Successor(val target: NodeName, val type: String, val flows: List<Flow>)

    /**
     * e.g. [id] `"flow_isSolventToCollectRejections"`, [name] `"No"`, [conditionExpression] `"${!solvent}"`
     */
    data class Flow(val id: String, val name: String?, val conditionExpression: String?, val defaultFlow: Boolean)
}
