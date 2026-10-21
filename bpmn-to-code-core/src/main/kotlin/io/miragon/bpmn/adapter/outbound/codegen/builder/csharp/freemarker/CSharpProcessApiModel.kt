package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.freemarker

/**
 * What `process-api.ftl` renders.
 * Strings are literals, identifiers or references, e.g. `processId = "\"bikeLeasing\""`,
 * and the template writes the code around them. Runtime types are written as members of the nested `Runtime` class,
 * e.g. `Runtime.ElementId`.
 *
 * @property processEngine a `BpmnEngine` member, e.g. `Zeebe`
 * @property flowNodes `null` when the process API has no `FlowNodes` section.
 */
internal data class CSharpProcessApiModel(
    val namespace: String,
    val typeName: String,
    val processId: String,
    val processEngine: String,
    val flowNodes: List<FlowNode>?,
) {

    /**
     * A facet is `null`, or empty, when the node does not have it.
     *
     * @property interfaces e.g. `Runtime.IFlowNode`, `Runtime.IHasJobType`, `Runtime.IHasVariables`
     * @property elementType a `BpmnElementType` member, e.g. `ServiceTask`
     * @property name a string literal, or `null`
     * @property jobType e.g. `ServiceTasks.SendContractDelegate`
     * @property message e.g. `Messages.MiraveloContractSigned`
     * @property attachedTo the class of the host, e.g. `ServiceTaskValidateApplication`
     * @property interiorStarts the classes of the subprocess' start elements, e.g. `StartEventAddressChanged`
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
        val error: NameAndCode?,
        val escalation: NameAndCode?,
        val attachedTo: String?,
        val interrupting: Boolean?,
        val variables: List<Variable>,
        val inputs: List<Mapping>,
        val outputs: List<Mapping>,
        val successors: List<Successor>,
        val interiorStarts: List<String>,
    )

    /**
     * @property type a `TimerType` member, e.g. `Duration`
     * @property expression e.g. `"P14D"`
     */
    data class Timer(val type: String, val expression: String)

    /**
     * Rendered as `new(Errors.MiraveloApplicationInvalid.Reference, Errors.MiraveloApplicationInvalid.Code)`, or as
     * `new("miravelo.applicationInvalid", "applicationInvalid")` when no shared constant holds them.
     */
    data class NameAndCode(val name: String, val code: String)

    /**
     * Rendered as `public Runtime.VariableName.Input ApplicationId { get; } = new(ProcessVariables.ApplicationId);`.
     *
     * @property subtype a `VariableName` subtype, e.g. `Input`
     */
    data class Variable(val name: String, val subtype: String, val nameConstant: String)

    /**
     * e.g. [name] `ApplicationId`, [target] `"applicationId"`, [source] `null`, [sourceExpression] `"${applicationId}"`
     */
    data class Mapping(val name: String, val target: String, val source: String, val sourceExpression: String)

    /**
     * @property target the class of the successor, e.g. `GatewayCollectRejections`
     * @property type the runtime type leading to it, e.g. `SequenceFlows`, or `AttachedBoundaryEvent` for a successor
     * reached without a flow
     * @property flows the sequence flows leading to [target]; empty when it is reached without one
     */
    data class Successor(val target: String, val type: String, val flows: List<Flow>)

    /**
     * e.g. [id] `"flow_isSolventToCollectRejections"`, [name] `"No"`, [conditionExpression] `"${!solvent}"`
     */
    data class Flow(val id: String, val name: String?, val conditionExpression: String?, val defaultFlow: Boolean)
}
