package io.miragon.bpmn.runtime

/**
 * What can follow a generated node, as offered by its `Next`: the [SequenceFlows] to an element, an
 * [AttachedBoundaryEvent], the [AssociatedCompensationHandler] of a compensation boundary event, or a single
 * [SequenceFlow] picked from [SequenceFlows.flows]. Each leads to a typed [target].
 */
sealed interface Successor<out TARGET : FlowNode> {
    val target: TARGET
}
