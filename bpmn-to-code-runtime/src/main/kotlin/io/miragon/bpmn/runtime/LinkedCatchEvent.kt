package io.miragon.bpmn.runtime

/**
 * A link catch event with the same link name as the current link throw event, in the same scope: the token continues
 * there, but no sequence flow leads there.
 */
data class LinkedCatchEvent<out TARGET : FlowNode>(override val target: TARGET) : Successor<TARGET>
