package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that has structural successors, listed in [outgoing].
 *
 * [FOLLOWING] is what the node leads to: the one element following it, or — when it leads to several — its
 * generated `Next` marker, implemented by every element it leads to, sequence-flow targets and attached boundary
 * events alike. A step or [flowsTo] accepts only such an element, which is the compile-time edge check.
 */
interface LeadsTo<FOLLOWING : FlowNode> : FlowNode {
    val outgoing: List<Successor<FOLLOWING>>

    /**
     * The sequence flows leading from this node to [target], typed to that element.
     */
    fun <M : FOLLOWING> flowsTo(target: M): SequenceFlows<M> {
        val flowsToTarget = outgoing.filterIsInstance<SequenceFlows<FOLLOWING>>().singleOrNull { it.target == target }
            ?: error("no sequence flow leads from $this to $target")
        return SequenceFlows(target, flowsToTarget.flows.map { it.leadingTo(target) })
    }
}
