package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode

/**
 * A compile-checked walk over a generated `Relations` navigation graph, accumulating the nodes it passes.
 *
 * Start with [from] at a named node (e.g. `ProcessPath.from(Relations.startEventSubmitRegistrationForm)`),
 * chain steps, then feed [ids] to your engine's existing string-based flow assertion — e.g.
 * `assertThat(instance).hasPassedInOrder(*path.ids.toTypedArray())`. The **edge steps** ([then] / [onto]) and
 * the **subprocess steps** ([enter] / [inside]) are checked against the model at compile time, so a model
 * change breaks the build at the exact edge that moved. [interruptedBy] checks the picked boundary successor
 * but names its carrier freely; [jumpTo] is the single fully-unchecked opt-out and is marked [RiskyNavigation].
 *
 * Ordering note: `hasPassedInOrder` is only meaningful within a single sequential branch. For parallel (AND)
 * branches, walk each branch separately and assert the unordered set via [nodesOf] + `hasPassed`.
 */
class ProcessPath<N : FlowNode> internal constructor(
    val current: N,
    private val recorded: List<FlowNode>,
) {

    /**
     * The nodes recorded so far, in walk order.
     */
    val nodes: List<FlowNode> get() = recorded

    /**
     * The recorded nodes' raw element ids, in walk order — ready for `hasPassedInOrder(*ids.toTypedArray())`.
     */
    val ids: List<String> get() = recorded.map { it.id.value }

    /**
     * The recorded nodes' ids as a distinct list
     */
    val distinctIds: List<String> get() = ids.distinct()

    companion object {
        /**
         * Starts a path at [start], recording it as the first node.
         */
        @JvmStatic
        fun <N : FlowNode> from(start: N): ProcessPath<N> {
            return ProcessPath(current = start, recorded = listOf(start))
        }
    }
}
