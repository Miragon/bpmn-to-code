package io.miragon.bpmn.runtime

/**
 * A boundary event attached to [attachedTo]. It shows up among its host's successors (`next`), but no sequence
 * flow leads to it — this type tells the two apart.
 */
interface BoundaryEvent<out HOST : FlowNode> : FlowNode {
    val attachedTo: HOST
    val isInterrupting: Boolean
}
