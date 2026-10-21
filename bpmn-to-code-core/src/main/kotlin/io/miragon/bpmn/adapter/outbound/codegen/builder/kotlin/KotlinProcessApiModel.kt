package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.freemarker.SharedName
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.SharedNameAndCode
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TypeReferences

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them: `ProcessId("bikeLeasing")`. Types are read through [types].
 *
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class KotlinProcessApiModel(
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNode>?,
    val types: TypeReferences,
) {

    /**
     * A facet is `null`, or empty, when the node does not have it.
     *
     * @property elementType a `BpmnElementType` constant, e.g. `SERVICE_TASK`
     * @property interfaces e.g. `HasSuccessors<ServiceTaskSendContract.Next>`, `HasJobType`, `HasVariables`
     * @property jobType e.g. `ServiceTasks.SEND_CONTRACT_DELEGATE`
     */
    data class FlowNode(
        val objectName: String,
        val elementType: String,
        val name: String?,
        val interfaces: List<String>,
        val eventType: String?,
        val elementId: String,
        val jobType: String?,
        val calledProcess: String?,
        val timer: Timer?,
        val message: SharedName?,
        val signal: SharedName?,
        val error: SharedNameAndCode?,
        val escalation: SharedNameAndCode?,
        val attachedTo: String?,
        val interrupting: Boolean?,
        val variables: List<Variable>,
        val inputs: List<Mapping>,
        val outputs: List<Mapping>,
        val successors: List<Successor>,
        val interiorStarts: List<InteriorStart>,
    )

    /**
     * @property type a `TimerType` constant, e.g. `DURATION`
     * @property value e.g. `"P14D"`
     */
    data class Timer(val type: String, val value: String)

    /**
     * Rendered as `val APPLICATION_ID: VariableName.Input = input(ProcessVariables.APPLICATION_ID)`.
     *
     * @property subtype a `VariableName` subtype, e.g. `Input`
     */
    data class Variable(val name: String, val subtype: String, val nameConstant: String)

    data class Mapping(val name: String, val target: String, val source: String?, val sourceExpression: String?)

    /**
     * @property type e.g. `SequenceFlows`, or `AttachedBoundaryEvent` for a successor reached without a flow
     * @property flows the sequence flows leading to [target]; empty when it is reached without one
     */
    data class Successor(val name: String, val type: String, val target: String, val flows: List<Flow>)

    data class Flow(val id: String, val name: String?, val conditionExpression: String?, val defaultFlow: Boolean)

    data class InteriorStart(val name: String, val objectName: String)
}
