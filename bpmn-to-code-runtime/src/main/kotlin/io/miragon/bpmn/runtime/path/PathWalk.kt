package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.AssociatedCompensationHandler
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.FlowScope
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlow
import io.miragon.bpmn.runtime.Successor
import java.util.function.Function

/**
 * A fluent, method-chained facade over [ProcessPath], usable from **Java and Kotlin** alike:
 * each step is an instance method (`walk.then(n -> n.x())` from Java, `walk.then { it.x }` from Kotlin),
 * so Java consumers get the same fluent, compile-checked navigation the Kotlin extension DSL offers —
 * without the static `ProcessPathStepsKt` call form.
 *
 * There is one recording engine, two call-site shapes: [PathWalk] **delegates** to [ProcessPath] and its step
 * functions. Kotlin code normally keeps using the richer extension DSL directly; [PathWalk] exists so the
 * library stays first-class from Java too.
 *
 * Two shape differences forced by Java's type system (vs. the Kotlin extension DSL): the terminal step is [end]
 * (an end event is not `HasSuccessors`, so it can't continue a chain; only [Trail.throwingCompensation] adds to it
 * afterwards), and descending into a subprocess names the subprocess explicitly ([enter] / [inside] take the
 * subprocess node as its [FlowScope]).
 */
class PathWalk<N : HasSuccessors<NEXT>, NEXT> internal constructor(private val path: ProcessPath<N>) {

    /**
     * Advances to a real successor and records it, together with the sequence flow walked when it is unambiguous
     * (see [flowIds]). `pick`'s input is the current node's `Next`, so only an actual successor compiles.
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> then(pick: Function<NEXT, out Successor<M>>): PathWalk<M, MNEXT> = PathWalk(path.traverse(pick.apply(path.current.next)))

    /**
     * Records the same successor [times] times in a row — for a sequential multi-instance activity or a
     * consecutive self-repeat.
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> thenMultipleTimes(times: Int, pick: Function<NEXT, out Successor<M>>): PathWalk<M, MNEXT> = PathWalk(path.traverse(pick.apply(path.current.next), times))

    /**
     * Advances onto a subprocess node **without** recording it — positions for [enter] / [inside].
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> onto(pick: Function<NEXT, out Successor<M>>): PathWalk<M, MNEXT> = PathWalk(path.onto { pick.apply(it) })

    /**
     * Terminal step: advances to a final successor (e.g. an end event) and stops, yielding a [Trail].
     */
    fun <M : FlowNode> end(pick: Function<NEXT, out Successor<M>>): Trail = Trail(path.traverse(pick.apply(path.current.next)))

    /**
     * Descends into the named subprocess [scope] and records the picked inner node — the re-anchor form of enter.
     */
    fun <S, M : HasSuccessors<MNEXT>, MNEXT> enter(scope: FlowScope<S>, pick: Function<S, M>): PathWalk<M, MNEXT> = PathWalk(path.enter(scope = scope) { pick.apply(it) })

    /**
     * Walks a subprocess interior in [block] (seeded from [scope]) and then continues **on the current
     * subprocess node** — so the following [then] is a plain, checked step after the subprocess. The block's
     * walked nodes are recorded; the current node afterwards is unchanged.
     */
    fun <S> inside(scope: FlowScope<S>, block: Function<S, Trail>): PathWalk<N, NEXT> {
        val interior = block.apply(scope.startEvents)
        return PathWalk(path.moveTo(node = path.current, nodesToRecord = interior.nodes, flowsToRecord = interior.flows))
    }

    /**
     * Leaves through a boundary event of [carrier] and records the picked continuation.
     */
    fun <C, M : HasSuccessors<MNEXT>, MNEXT> interruptedBy(
        carrier: HasSuccessors<C>,
        pick: Function<C, out Successor<M>>,
    ): PathWalk<M, MNEXT> = PathWalk(path.traverse(pick.apply(carrier.next)))

    /**
     * Records a compensation the current event throws — the handler picked from the compensation [boundaryEvent] —
     * and stays on the current node. Pass `includeBoundaryEvent = true` to record the boundary event as well, which
     * only Zeebe reports as passed. Unlike the Kotlin step, this does not check that the current node is a
     * compensation throw event: a method cannot narrow the node type of its [PathWalk].
     */
    @JvmOverloads
    fun <C, H : FlowNode> throwingCompensation(
        boundaryEvent: HasSuccessors<C>,
        includeBoundaryEvent: Boolean = false,
        pick: Function<C, out AssociatedCompensationHandler<H>>,
    ): PathWalk<N, NEXT> = PathWalk(path.recordCompensation(boundaryEvent = boundaryEvent, includeBoundaryEvent = includeBoundaryEvent, handler = pick.apply(boundaryEvent.next)))

    /**
     * Unchecked re-anchor to an arbitrary node — does not record. The escape hatch; prefer the checked steps.
     */
    @RiskyNavigation
    @OptIn(RiskyNavigation::class)
    fun <M : HasSuccessors<MNEXT>, MNEXT> jumpTo(node: M): PathWalk<M, MNEXT> = PathWalk(path.jumpTo(node))

    /**
     * The nodes recorded so far, in walk order.
     */
    val nodes: List<FlowNode> get() = path.nodes

    /**
     * The recorded nodes' ids, in walk order.
     */
    val ids: Array<String> get() = path.ids

    /**
     * The recorded nodes' ids, deduplicated.
     */
    val distinctIds: Array<String> get() = path.distinctIds

    /**
     * The ids of the sequence flows walked, in walk order.
     */
    val flowIds: Array<String> get() = path.flowIds

    /**
     * The terminal result of a [PathWalk] (produced by [end] or a subprocess [inside] block) — no further
     * navigation, just the recorded [ids] / [nodes] and the compensations its last node triggered.
     */
    class Trail internal constructor(private val path: ProcessPath<*>) {

        /**
         * Records a compensation the terminal node triggered, like [PathWalk.throwingCompensation] — for an end event
         * that throws a compensation.
         */
        @JvmOverloads
        fun <C, H : FlowNode> throwingCompensation(
            boundaryEvent: HasSuccessors<C>,
            includeBoundaryEvent: Boolean = false,
            pick: Function<C, out AssociatedCompensationHandler<H>>,
        ): Trail = Trail(path.recordCompensation(boundaryEvent = boundaryEvent, includeBoundaryEvent = includeBoundaryEvent, handler = pick.apply(boundaryEvent.next)))

        /**
         * The nodes recorded so far, in walk order.
         */
        val nodes: List<FlowNode> get() = path.nodes

        /**
         * The recorded nodes' ids, in walk order — ready for `hasPassedInOrder(*ids)`.
         */
        val ids: Array<String> get() = path.ids

        /**
         * The recorded nodes' ids, deduplicated.
         */
        val distinctIds: Array<String> get() = path.distinctIds

        /**
         * The ids of the sequence flows walked, in walk order.
         */
        val flowIds: Array<String> get() = path.flowIds

        internal val flows: List<SequenceFlow<*>> get() = path.flows
    }

    companion object {

        /**
         * Starts a walk at [start], recording it as the first node.
         */
        @JvmStatic
        fun <N : HasSuccessors<NEXT>, NEXT> from(start: N): PathWalk<N, NEXT> = PathWalk(ProcessPath.from(start))

        /**
         * Union of separately-walked branches into one unordered, deduplicated set (parallel AND branches).
         */
        @JvmStatic
        @SafeVarargs
        fun nodesOf(vararg branches: List<FlowNode>): List<FlowNode> = io.miragon.bpmn.runtime.path.nodesOf(*branches)
    }
}
