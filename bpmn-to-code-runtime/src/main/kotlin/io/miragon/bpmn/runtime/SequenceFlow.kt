package io.miragon.bpmn.runtime

/**
 * One outgoing `bpmn:sequenceFlow` of a generated `Flow` node, as exposed by its `Flows` holder.
 *
 * [conditionExpression] is the raw expression text from the model (`${…}` for Camunda 7 / Operaton,
 * `=…` FEEL for Zeebe) or `null` when the flow is unconditional; [isDefault] marks the source's
 * default flow. [target] is the typed node the flow leads to, so a test can assert both the
 * condition and where it goes without leaving the generated API.
 */
data class SequenceFlow<out TARGET : FlowNode>(
    val id: ElementId,
    val name: String?,
    val conditionExpression: String?,
    val isDefault: Boolean,
    val target: TARGET,
)
