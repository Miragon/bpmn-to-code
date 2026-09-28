package io.miragon.bpmn.runtime

/**
 * A [FlowNode] with outgoing sequence flows, exposed as typed [SequenceFlow]s behind [outgoingFlows].
 *
 * [OUTGOING] is the node's own generated `OutgoingFlows` holder, named after the elements the flows lead to, so
 * `outgoingFlows()` keeps per-node typing while [HasOutgoingFlows] adds a generic entry point on top — the twin of
 * [HasSuccessors] for sequence flows instead of elements.
 */
interface HasOutgoingFlows<out OUTGOING> : FlowNode {
    fun outgoingFlows(): OUTGOING
}
