package io.miragon.bpmn.runtime

/**
 * A [FlowNode] with outgoing sequence flows, exposed as typed [SequenceFlow] edges behind [flows].
 *
 * [FLOWS] is the node's own generated `Flows` holder, so `flows()` keeps per-node edge typing while
 * [HasFlows] adds a generic entry point on top — the twin of [HasSuccessors] for edges instead of nodes.
 */
interface HasFlows<out FLOWS> : FlowNode {
    fun flows(): FLOWS
}
