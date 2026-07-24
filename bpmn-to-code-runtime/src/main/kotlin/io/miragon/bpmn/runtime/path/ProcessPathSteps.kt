package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.HasInner
import io.miragon.bpmn.runtime.Navigable
import io.miragon.bpmn.runtime.NavigationScope

/**
 * Edge step: advance to a real successor of the current node and record it. The lambda's parameter `it` is
 * the current node's `Next`, so `it.<successor>` autocompletes — and only an actual successor compiles.
 *
 * [repeatTimes] records the same node several times in a row (default 1), for a sequential multi-instance
 * activity or a genuine consecutive self-repeat. Multi-node cycles are written out explicitly.
 */
fun <NEXT, M : FlowNode> ProcessPath<out Navigable<NEXT>>.then(
    repeatTimes: Int = 1,
    pick: (NEXT) -> M,
): ProcessPath<M> {
    val node = pick(current.then())
    return ProcessPath(node, nodes + List(repeatTimes) { node })
}

/**
 * Edge step without recording: advance to a real successor but leave it out of the asserted path — for
 * gateways / subprocess markers whose runtime order isn't guaranteed. Traversal is still compile-checked.
 */
fun <NEXT, M : FlowNode> ProcessPath<out Navigable<NEXT>>.skip(pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(current.then())
    return ProcessPath(node, nodes)
}

/**
 * Descend into the current subprocess node's interior and record the entered inner node. The lambda's `it`
 * is the interior's start `Next`, so `it.<start>` autocompletes — only a real inner start compiles. On the
 * same subprocess node [then] continues *after* the subprocess, [enter] goes *inside* it.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasInner<NavigationScope<NEXT>>>.enter(pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(current.inner().then())
    return ProcessPath(node, nodes + node)
}

/**
 * Descend into an explicitly named interior scope — the re-anchor form of [enter], for entering a subprocess
 * from a position where it isn't the current node (e.g. `enter(Relations.SubProcess.Inner) { it.start }`).
 */
fun <NEXT, M : FlowNode> ProcessPath<*>.enter(inner: NavigationScope<NEXT>, pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(inner.then())
    return ProcessPath(node, nodes + node)
}

/**
 * Leave a subprocess through its **normal** continuation: re-anchor to [subprocess]`.then()` and record the
 * picked successor. `it.<successor>` autocompletes exactly what follows the subprocess. The picked successor
 * is compile-checked against the subprocess' model; only the [subprocess] reference itself is named freely.
 */
fun <NEXT, M : FlowNode> ProcessPath<*>.continueAfter(
    subprocess: Navigable<NEXT>,
    pick: (NEXT) -> M,
): ProcessPath<M> {
    val node = pick(subprocess.then())
    return ProcessPath(node, nodes + node)
}

/**
 * Leave an activity/subprocess through an attached **boundary** event: re-anchor to [carrier]`.then()` and
 * record the picked boundary continuation. Same machinery as [continueAfter], but named for the reader — this
 * is the interruption path, not the normal exit. Covers interrupting timers and error boundaries; `it` offers
 * exactly the carrier's boundary events, compile-checked.
 */
fun <NEXT, M : FlowNode> ProcessPath<*>.interruptedBy(
    carrier: Navigable<NEXT>,
    pick: (NEXT) -> M,
): ProcessPath<M> {
    val node = pick(carrier.then())
    return ProcessPath(node, nodes + node)
}

/**
 * Walk a subprocess interior in a scoped block, then continue **on the subprocess node itself** — so the step
 * after the block is a plain, typed [then] without naming the subprocess again. The block starts positioned on
 * the subprocess node (so it opens with `enter { it.start }`); its walked nodes are recorded, and the current
 * node afterwards is the subprocess (unchanged). Nesting works: each block captures its own subprocess via the
 * closure, so there is no lost-parent limitation.
 */
fun <N : FlowNode> ProcessPath<N>.inside(block: ProcessPath<N>.() -> ProcessPath<*>): ProcessPath<N> {
    val walked = ProcessPath(current, emptyList()).block()
    return ProcessPath(current, nodes + walked.nodes)
}

/**
 * Union of separately-walked branch paths into one unordered, deduplicated "was passed" set — for parallel
 * (AND) branches whose relative order isn't defined. Feed the result to `hasPassed` / `hasNotPassed`.
 */
fun nodesOf(vararg branches: List<FlowNode>): List<FlowNode> {
    return branches.flatMap { it }.distinct()
}

/** Opt-in gate for the unchecked [jumpTo] escape hatch, so it stands out in code and review. */
@RequiresOptIn(
    message = "Unchecked navigation jump — bypasses the model adjacency check. " +
        "Prefer then / enter / continueAfter / interruptedBy where possible.",
)
@Retention(AnnotationRetention.BINARY)
annotation class RiskyNavigation

/**
 * Re-anchor to an arbitrary node **without** recording it and **without** checking adjacency — the last-resort
 * escape hatch, e.g. stepping back to a parallel fork to walk its second branch in one chain. Marked
 * [RiskyNavigation] so every use is an explicit `@OptIn`; prefer the checked steps.
 */
@RiskyNavigation
fun <M : FlowNode> ProcessPath<*>.jumpTo(node: M): ProcessPath<M> {
    return ProcessPath(node, nodes)
}
