package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import freemarker.template.TemplateHashModel
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.SharedNameAndCode

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them: `PROCESS_ID: "bikeLeasing"`. Runtime names are read through
 * [runtimeTypes] and [runtimeValues].
 *
 * @property processEngine e.g. `"ZEEBE"`
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class TypeScriptProcessApiModel(
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNode>?,
    val runtimeTypes: TemplateHashModel,
    val runtimeValues: TemplateHashModel,
) {

    /**
     * A facet is `null`, or empty, when the node does not have it.
     *
     * @property interfaces e.g. `Runtime.FlowNode`, `Runtime.HasSuccessors`, `Runtime.HasJobType`
     * @property elementType e.g. `"SERVICE_TASK"`
     * @property name a string literal, or `null`
     * @property jobType e.g. `ServiceTasks.SEND_CONTRACT_DELEGATE`, or a literal when no shared constant holds it
     * @property message e.g. `Messages.MIRAVELO_CONTRACT_SIGNED`
     * @property attachedTo the class of a boundary event's host, e.g. `ServiceTaskValidateApplication`
     */
    data class FlowNode(
        val className: String,
        val interfaces: List<String>,
        val elementId: String,
        val elementType: String,
        val eventType: String?,
        val name: String,
        val jobType: String?,
        val calledProcess: String?,
        val timer: Timer?,
        val message: String?,
        val signal: String?,
        val error: SharedNameAndCode?,
        val escalation: SharedNameAndCode?,
        val interrupting: Boolean?,
        val throwsCompensation: Boolean,
        val attachedTo: String?,
        val variables: List<Variable>,
        val inputs: List<Mapping>,
        val outputs: List<Mapping>,
        val successors: List<Successor>,
        val interiorStarts: List<InteriorStart>,
    )

    /**
     * Rendered as `timer = { type: "DURATION", timerValue: "P14D" } as const`.
     */
    data class Timer(val type: String, val value: String)

    /**
     * Rendered as `APPLICATION_ID: Runtime.input(ProcessVariables.APPLICATION_ID)`.
     *
     * @property subtype a `VariableName` subtype, e.g. `Input`
     */
    data class Variable(val key: String, val subtype: String, val nameConstant: String)

    /**
     * e.g. [key] `APPLICATION_ID`, [target] `"applicationId"`, [source] `null`, [sourceExpression] `"${applicationId}"`
     */
    data class Mapping(val key: String, val target: String, val source: String, val sourceExpression: String)

    /**
     * @property key e.g. `gatewayCollectRejections`
     * @property target the class of the successor, e.g. `GatewayCollectRejections`
     * @property type the runtime type leading to it, e.g. `SequenceFlows`, or `AttachedBoundaryEvent` for a successor
     * reached without a flow
     * @property flows the sequence flows leading to [target]; empty when it is reached without one
     */
    data class Successor(val key: String, val target: String, val type: String, val flows: List<Flow>)

    /**
     * e.g. [id] `"flow_isSolventToCollectRejections"`, [name] `"No"`, [conditionExpression] `"${!solvent}"`
     */
    data class Flow(val id: String, val name: String?, val conditionExpression: String?, val defaultFlow: Boolean)

    /**
     * e.g. [key] `startEventAddressChanged` and [className] `StartEventAddressChanged`
     */
    data class InteriorStart(val key: String, val className: String)
}
