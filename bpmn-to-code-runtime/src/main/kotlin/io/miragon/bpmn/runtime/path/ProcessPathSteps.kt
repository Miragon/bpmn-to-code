package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.FlowScope
import io.miragon.bpmn.runtime.HasOutgoingFlows
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlow

/**
 * Edge step: advance to a real successor of the current node and record it. The lambda's parameter `it` is
 * the current node's `Next`, so `it.<successor>` autocompletes — and only an actual successor compiles.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.then(pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(current.next)
    return moveTo(node, listOf(node))
}

/**
 * Sequence-flow step: advance along an outgoing sequence flow of the current node and record both its target and
 * the flow itself (see [ProcessPath.flowIds]). The lambda's parameter `it` is the current node's `OutgoingFlows`,
 * so `it.to<Element>` autocompletes; where several flows lead to the same element, pick one of the list.
 */
fun <OUTGOING, M : FlowNode> ProcessPath<out HasOutgoingFlows<OUTGOING>>.via(pick: (OUTGOING) -> SequenceFlow<M>): ProcessPath<M> {
    val flow = pick(current.outgoingFlows)
    return moveTo(flow.target, listOf(flow.target), listOf(flow))
}

/**
 * Edge step that records the same successor [repeatTimes] times in a row — for a sequential multi-instance
 * activity or a genuine consecutive self-repeat. Multi-node cycles are written out with plain [then] instead.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.thenMultipleTimes(
    repeatTimes: Int,
    pick: (NEXT) -> M,
): ProcessPath<M> {
    val node = pick(current.next)
    return moveTo(node, List(repeatTimes) { node })
}

/**
 * Position **onto** a subprocess node ([subprocess], a compile-checked successor of the current node) without
 * recording it, ready to descend with [enter] or walk it with [inside]. The lambda's `it` is the current
 * node's `Next`, so `it.<subprocess>` autocompletes — one lambda, so the IDE completes it immediately.
 *
 * A subprocess is a scope *bracket*, not a point in the ordered flow, so its marker isn't recorded here; assert
 * it separately via `hasPassed(...)`. Reads as two simple steps: `onto { it.sub }.enter { it.start }`.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.onto(subprocess: (NEXT) -> M): ProcessPath<M> {
    val node = subprocess(current.next)
    return moveTo(node, emptyList())
}

/**
 * Descend into the current subprocess node's interior and record the entered inner node. The lambda's `it`
 * is the subprocess's `Start`, so `it.<start>` autocompletes — only a real inner start compiles. Reach the
 * subprocess node first with [onto] (checked edge) — e.g. `onto { it.sub }.enter { it.start }`.
 *
 * To leave the subprocess again: a **normal** full walk uses [inside] (which resumes on the subprocess node
 * automatically), a **boundary** interruption uses [interruptedBy]. A bare `onto { … }.enter { … }` chain with
 * neither is a dead end — once inside you can only continue out via [inside] or [interruptedBy].
 */
fun <START, M : FlowNode> ProcessPath<out FlowScope<START>>.enter(pick: (START) -> M): ProcessPath<M> {
    val node = pick(current.startEvents)
    return moveTo(node, listOf(node))
}

/**
 * Descend into an explicitly named subprocess — the re-anchor form of [enter], for entering a subprocess
 * from a position where it isn't the current node (e.g. `enter(Flow.SubProcess) { it.start }`).
 */
fun <START, M : FlowNode> ProcessPath<*>.enter(scope: FlowScope<START>, pick: (START) -> M): ProcessPath<M> {
    val node = pick(scope.startEvents)
    return moveTo(node, listOf(node))
}

/**
 * Leave an activity/subprocess through an attached **boundary** event: re-anchor to [carrier]`.next` and
 * record the picked boundary continuation — the token leaves the interior *early* via the boundary, which is
 * why this is a re-anchor and not expressible with [inside]. Covers interrupting timers and error boundaries;
 * `it` offers exactly the carrier's boundary events, compile-checked.
 */
fun <NEXT, M : FlowNode> ProcessPath<*>.interruptedBy(
    carrier: HasSuccessors<NEXT>,
    pick: (NEXT) -> M,
): ProcessPath<M> {
    val node = pick(carrier.next)
    return moveTo(node, listOf(node))
}

/**
 * Walk the **current subprocess** node's interior in a scoped block, then continue **on the subprocess node
 * itself** — so the step after the block is a plain, typed [then] without naming the subprocess again. Only
 * callable on a subprocess node (reached via [onto]); the block opens with `enter { it.start }`, its walked
 * nodes are recorded, and the current node afterwards is the subprocess (unchanged). Nesting works: an inner
 * subprocess is entered with its own `onto { … }.inside { … }`, each block capturing its subprocess via the
 * closure.
 */
fun <START, N : FlowScope<START>> ProcessPath<N>.inside(
    block: ProcessPath<N>.() -> ProcessPath<*>,
): ProcessPath<N> {
    val walked = ProcessPath(current = current, recorded = emptyList<FlowNode>()).block()
    return moveTo(current, walked.nodes, walked.flows)
}

/**
 * Union of separately-walked branch paths into one unordered, deduplicated "was passed" set — for parallel
 * (AND) branches whose relative order isn't defined. Feed the result to `hasPassed` / `hasNotPassed`.
 */
@SafeVarargs
fun nodesOf(vararg branches: List<FlowNode>): List<FlowNode> = branches.flatMap { it }.distinct()

/**
 * Re-anchor to an arbitrary node **without** recording it and **without** checking adjacency — the last-resort
 * escape hatch, e.g. stepping back to a parallel fork to walk its second branch in one chain. Marked
 * [RiskyNavigation] so every use is an explicit `@OptIn`; prefer the checked steps.
 */
@RiskyNavigation
fun <M : FlowNode> ProcessPath<*>.jumpTo(node: M): ProcessPath<M> = moveTo(node, emptyList())
