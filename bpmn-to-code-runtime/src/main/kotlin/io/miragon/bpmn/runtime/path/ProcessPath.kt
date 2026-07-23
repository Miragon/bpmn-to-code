package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode

/**
 * A compile-checked walk over a generated `Relations` navigation graph, accumulating the nodes it passes.
 *
 * Build a path by chaining steps from a start node, then feed [ids] to your engine's existing string-based
 * flow assertion — e.g. `assertThat(instance).hasPassedInOrder(*path.ids.toTypedArray())`. Every **edge step**
 * ([then] / [via] with a lambda) is checked against the model at compile time, so a model change breaks the
 * build at the exact edge that moved. The **re-anchor steps** ([then]/[back] with a node) are the visible,
 * syntactically distinct opt-outs used to cross a subprocess scope or step back to a parallel fork.
 *
 * Ordering note: `hasPassedInOrder` is only meaningful within a single sequential branch. For parallel (AND)
 * branches, walk each branch separately and assert the unordered set via [passedNodes] + `hasPassed`.
 */
class ProcessPath<N : FlowNode> internal constructor(
    val current: N,
    private val recorded: List<FlowNode>,
) {

    /** The nodes recorded so far, in walk order. */
    val nodes: List<FlowNode> get() = recorded

    /** The recorded nodes' raw element ids, in walk order — ready for `hasPassedInOrder(*ids.toTypedArray())`. */
    val ids: List<String> get() = recorded.map { it.id.value }

    companion object {
        /**
         * Starts a path at [start], recording it as the first node.
         */
        fun <N : FlowNode> from(start: N): ProcessPath<N> {
            return ProcessPath(start, listOf(start))
        }
    }
}
