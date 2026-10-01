package io.miragon.bpmn.runtime

/**
 * A boundary event attached to [attachedTo]. It shows up among its host's successors (`outgoing`), but no sequence
 * flow leads to it — this type tells the two apart.
 */
interface BoundaryEvent<out HOST : FlowNode> : Event {
    val attachedTo: HOST
    val isInterrupting: Boolean
}
