package io.miragon.bpmn.runtime

/**
 * What can follow a generated node, as listed in its `outgoing`: the [SequenceFlows] to an element, an
 * [AttachedBoundaryEvent], or a single [SequenceFlow] picked from [SequenceFlows.flows]. Each leads to a typed [target].
 */
sealed interface Successor<out TARGET : FlowNode> {
    val target: TARGET
}
