package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.AttachedBoundaryEvent
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.FlowScope
import io.miragon.bpmn.runtime.HasSuccessors
import io.miragon.bpmn.runtime.SequenceFlow
import io.miragon.bpmn.runtime.SequenceFlows
import io.miragon.bpmn.runtime.Successor

/**
 * Edge step: advance to a successor of the current node and record its target. The lambda's parameter `it` is
 * the current node's `Next`, so `it.<successor>` autocompletes — and only an actual successor compiles. The
 * sequence flow is recorded too (see [ProcessPath.flowIds]) when it is unambiguous: a picked [SequenceFlow], or a
 * [SequenceFlows] with exactly one flow.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.then(pick: (NEXT) -> Successor<M>): ProcessPath<M> = traverse(pick(current.next))

/**
 * Successor step that records the same successor [repeatTimes] times in a row — for a sequential multi-instance
 * activity or a genuine consecutive self-repeat. The flow leading there is recorded once, as the engine takes it
 * once. Multi-node cycles are written out with plain [then] instead.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.thenMultipleTimes(
    repeatTimes: Int,
    pick: (NEXT) -> Successor<M>,
): ProcessPath<M> = traverse(pick(current.next), repeatTimes)

/**
 * Position **onto** a subprocess node ([subprocess], a compile-checked successor of the current node) without
 * recording it, ready to descend with [enter] or walk it with [inside]. The lambda's `it` is the current
 * node's `Next`, so `it.<subprocess>` autocompletes — one lambda, so the IDE completes it immediately.
 *
 * A subprocess is a scope *bracket*, not a point in the ordered flow, so its marker isn't recorded here; assert
 * it separately via `hasPassed(...)`. The flow into the subprocess is recorded like in [then]. Reads as two simple
 * steps: `onto { it.sub }.enter { it.start }`.
 */
fun <NEXT, M : FlowNode> ProcessPath<out HasSuccessors<NEXT>>.onto(subprocess: (NEXT) -> Successor<M>): ProcessPath<M> {
    val successor = subprocess(current.next)
    return moveTo(node = successor.target, nodesToRecord = emptyList(), flowsToRecord = listOfNotNull(successor.takenFlow()))
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
 * from a position where it isn't the current node (e.g. `enter(FlowNodes.SubProcess) { it.start }`).
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
fun <NEXT, M : FlowNode> ProcessPath<*>.interruptedBy(carrier: HasSuccessors<NEXT>, pick: (NEXT) -> Successor<M>): ProcessPath<M> = traverse(pick(carrier.next))

/**
 * Walk the **current subprocess** node's interior in a scoped block, then continue **on the subprocess node
 * itself** — so the step after the block is a plain, typed [then] without naming the subprocess again. Only
 * callable on a subprocess node (reached via [onto]); the block opens with `enter { it.start }`, its walked
 * nodes are recorded, and the current node afterwards is the subprocess (unchanged). Nesting works: an inner
 * subprocess is entered with its own `onto { … }.inside { … }`, each block capturing its subprocess via the
 * closure.
 */
fun <START, N : FlowScope<START>> ProcessPath<N>.inside(block: ProcessPath<N>.() -> ProcessPath<*>): ProcessPath<N> {
    val walked = ProcessPath(current = current, recorded = emptyList<FlowNode>()).block()
    return moveTo(node = current, nodesToRecord = walked.nodes, flowsToRecord = walked.flows)
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

internal fun <M : FlowNode> ProcessPath<*>.traverse(successor: Successor<M>, repeatTimes: Int = 1): ProcessPath<M> = moveTo(node = successor.target, nodesToRecord = List(repeatTimes) { successor.target }, flowsToRecord = listOfNotNull(successor.takenFlow()))

private fun Successor<*>.takenFlow(): SequenceFlow<*>? = when (this) {
    is SequenceFlow -> this
    is SequenceFlows -> flows.singleOrNull()
    is AttachedBoundaryEvent -> null
}
