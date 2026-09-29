package io.miragon.bpmn.runtime

/**
 * One outgoing `bpmn:sequenceFlow` of a generated `FlowNodes` node, as exposed by its `OutgoingFlows` holder
 * under the name of the element it leads to.
 *
 * [conditionExpression] is the raw expression text from the model (`${…}` for Camunda 7 / Operaton,
 * `=…` FEEL for Zeebe) or `null` when the flow is unconditional; [isDefault] marks the source's
 * default flow. [target] is the typed node the flow leads to, so a test can assert both the
 * condition and where it goes without leaving the generated API.
 */
data class SequenceFlow<out TARGET : FlowNode>(
    val id: ElementId,
    val name: String? = null,
    val conditionExpression: String? = null,
    val isDefault: Boolean = false,
    val target: TARGET,
) {
    constructor(id: ElementId, target: TARGET) : this(id, null, null, false, target)
}
