package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.NavigationScope
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
 * (an end event is not `HasSuccessors`, so it can't continue a chain), and descending into a subprocess names the
 * interior scope explicitly ([enter] / [inside] take the generated `Inner` scope).
 */
class PathWalk<N : HasSuccessors<NEXT>, NEXT> internal constructor(
    private val path: ProcessPath<N>,
) {

    /**
     * Advances to a real successor and records it. `pick`'s input is the current node's `Next`, so only an
     * actual successor compiles.
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> then(pick: Function<NEXT, M>): PathWalk<M, MNEXT> =
        PathWalk(path.then { pick.apply(it) })

    /**
     * Records the same successor [times] times in a row — for a sequential multi-instance activity or a
     * consecutive self-repeat.
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> thenMultipleTimes(times: Int, pick: Function<NEXT, M>): PathWalk<M, MNEXT> =
        PathWalk(path.thenMultipleTimes(repeatTimes = times) { pick.apply(it) })

    /**
     * Advances onto a subprocess node **without** recording it — positions for [enter] / [inside].
     */
    fun <M : HasSuccessors<MNEXT>, MNEXT> onto(pick: Function<NEXT, M>): PathWalk<M, MNEXT> =
        PathWalk(path.onto { pick.apply(it) })

    /**
     * Terminal step: advances to a final successor (e.g. an end event) and stops, yielding a [Trail].
     */
    fun <M : FlowNode> end(pick: Function<NEXT, M>): Trail =
        Trail(path.then { pick.apply(it) })

    /**
     * Descends into a named interior [scope] and records the picked inner node — the re-anchor form of enter.
     */
    fun <S, M : HasSuccessors<MNEXT>, MNEXT> enter(scope: NavigationScope<S>, pick: Function<S, M>): PathWalk<M, MNEXT> =
        PathWalk(path.enter(inner = scope) { pick.apply(it) })

    /**
     * Walks a subprocess interior in [block] (seeded from [scope]) and then continues **on the current
     * subprocess node** — so the following [then] is a plain, checked step after the subprocess. The block's
     * walked nodes are recorded; the current node afterwards is unchanged.
     */
    fun <S> inside(scope: NavigationScope<S>, block: Function<S, Trail>): PathWalk<N, NEXT> {
        val interior = block.apply(scope.then())
        return PathWalk(ProcessPath(current = path.current, recorded = path.nodes + interior.nodes))
    }

    /**
     * Leaves through a boundary event of [carrier] and records the picked continuation.
     */
    fun <C, M : HasSuccessors<MNEXT>, MNEXT> interruptedBy(
        carrier: HasSuccessors<C>,
        pick: Function<C, M>,
    ): PathWalk<M, MNEXT> =
        PathWalk(path.interruptedBy(carrier = carrier) { pick.apply(it) })

    /**
     * Unchecked re-anchor to an arbitrary node — does not record. The escape hatch; prefer the checked steps.
     */
    @RiskyNavigation
    @OptIn(RiskyNavigation::class)
    fun <M : HasSuccessors<MNEXT>, MNEXT> jumpTo(node: M): PathWalk<M, MNEXT> =
        PathWalk(path.jumpTo(node))

    /**
     * The nodes recorded so far, in walk order.
     */
    val nodes: List<FlowNode> get() = path.nodes

    /**
     * The recorded nodes' ids, in walk order.
     */
    val ids: List<String> get() = path.ids

    /**
     * The recorded nodes' ids, deduplicated.
     */
    val distinctIds: List<String> get() = path.distinctIds

    /**
     * The terminal result of a [PathWalk] (produced by [end] or a subprocess [inside] block) — no further
     * navigation, just the recorded [ids] / [nodes].
     */
    class Trail internal constructor(private val path: ProcessPath<*>) {

        /**
         * The nodes recorded so far, in walk order.
         */
        val nodes: List<FlowNode> get() = path.nodes

        /**
         * The recorded nodes' ids, in walk order — ready for `hasPassedInOrder(*ids.toTypedArray())`.
         */
        val ids: List<String> get() = path.ids

        /**
         * The recorded nodes' ids, deduplicated.
         */
        val distinctIds: List<String> get() = path.distinctIds
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
        fun nodesOf(vararg branches: List<FlowNode>): List<FlowNode> = branches.flatMap { it }.distinct()
    }
}
