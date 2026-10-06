package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.SequenceFlow

/**
 * A compile-checked walk over a generated `FlowNodes` navigation graph, accumulating the nodes it passes.
 *
 * Start with [from] at a named node (e.g. `ProcessPath.from(FlowNodes.StartEventSubmitRegistrationForm)`),
 * chain steps, then feed [ids] to your engine's existing string-based flow assertion — e.g.
 * `assertThat(instance).hasPassedInOrder(*path.ids.toTypedArray())`. The **edge steps** ([then] / [onto]) and
 * the **subprocess steps** ([enter] / [inside]) are checked against the model at compile time, so a model
 * change breaks the build at the exact edge that moved. [interruptedBy] and [throwingCompensation] check the picked
 * successor but name its carrier freely; [jumpTo] is the single fully-unchecked opt-out and is marked [RiskyNavigation].
 *
 * Ordering note: `hasPassedInOrder` is only meaningful within a single sequential branch. For parallel (AND)
 * branches, walk each branch separately and assert the unordered set via [nodesOf] + `hasPassed`.
 */
class ProcessPath<N : FlowNode> internal constructor(
    val current: N,
    private val recorded: List<FlowNode>,
    private val takenFlows: List<SequenceFlow<*>> = emptyList(),
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

    /**
     * The sequence flows recorded so far, in walk order.
     */
    internal val flows: List<SequenceFlow<*>> get() = takenFlows

    /**
     * The ids of the recorded sequence flows, in walk order — for comparing against the engine's taken sequence
     * flows. [then] and [onto] record the flow they walk when it is unambiguous; boundary events, compensation
     * handlers, [enter] and several flows to the same element record none.
     */
    val flowIds: List<String> get() = takenFlows.map { it.id.value }

    /**
     * The one way every step moves on: to [node], recording [nodesToRecord] and [flowsToRecord] after what is
     * already recorded.
     */
    internal fun <M : FlowNode> moveTo(
        node: M,
        nodesToRecord: List<FlowNode>,
        flowsToRecord: List<SequenceFlow<*>> = emptyList(),
    ): ProcessPath<M> = ProcessPath(current = node, recorded = recorded + nodesToRecord, takenFlows = takenFlows + flowsToRecord)

    companion object {
        /**
         * Starts a path at [start], recording it as the first node.
         */
        @JvmStatic
        fun <N : FlowNode> from(start: N): ProcessPath<N> = ProcessPath(current = start, recorded = listOf(start))
    }
}
