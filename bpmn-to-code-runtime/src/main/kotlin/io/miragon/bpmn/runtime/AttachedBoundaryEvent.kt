package io.miragon.bpmn.runtime

/**
 * A boundary event attached to the current node (BPMN `attachedToRef`): the token can leave through it, but no
 * sequence flow leads there.
 */
data class AttachedBoundaryEvent<out TARGET : FlowNode>(override val target: TARGET) : Successor<TARGET>
