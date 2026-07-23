package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.HasInner
import io.miragon.bpmn.runtime.Navigable
import io.miragon.bpmn.runtime.NavigationScope

/**
 * Edge step: advance to a real successor of the current node and record it. The lambda's parameter `it` is
 * the current node's `Next`, so `it.<successor>` autocompletes — and only an actual successor compiles.
 */
fun <NEXT, M : FlowNode> ProcessPath<out Navigable<NEXT>>.then(pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(current.then())
    return ProcessPath(node, nodes + node)
}

/**
 * Edge step without recording: advance to a real successor but leave it out of the asserted path — for
 * gateways / subprocess markers whose runtime order isn't guaranteed.
 */
fun <NEXT, M : FlowNode> ProcessPath<out Navigable<NEXT>>.via(pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(current.then())
    return ProcessPath(node, nodes)
}

/**
 * Re-anchor step: jump to an explicitly named node (scope entry/exit) and record it. Works from terminals.
 *
 * Note there is deliberately **no** `then { … }` `{ }` overload: it would be ambiguous with the edge step
 * [then] (`NEXT.() -> M`), so *every* `then { successor }` call would stop compiling. Recording re-anchors
 * therefore stay in the `then(node)` value form; only [back] offers the `{ }` variant.
 */
fun <M : FlowNode> ProcessPath<*>.then(node: M): ProcessPath<M> {
    return ProcessPath(node, nodes + node)
}

/**
 * Re-anchor step without recording: step back to an earlier branch point (e.g. a parallel fork).
 */
fun <M : FlowNode> ProcessPath<*>.back(node: M): ProcessPath<M> {
    return ProcessPath(node, nodes)
}

/**
 * Re-anchor step without recording, `{ }` form: same as [back] with a node, but the target is given as a
 * **fully qualified** reference inside braces (e.g. `back { Order.GatewayFork }`) for a consistent read.
 * The lambda has **no `Next` receiver** — it's the unchecked scope/branch hop, just spelled with braces.
 */
fun <M : FlowNode> ProcessPath<*>.back(node: () -> M): ProcessPath<M> {
    return back(node())
}

/**
 * Descend into the current subprocess node's interior and record the entered inner node. The lambda's `it`
 * is the interior's start `Next`, so `it.<start>` autocompletes — only a real inner start compiles. On the
 * same subprocess node `then { … }` continues *after* the subprocess, `enter { … }` goes *inside* it.
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
 * Leave a subprocess: re-anchor to the named subprocess node's continuation (its `then()`) and record the
 * picked successor — the typed, compile-checked way back out. Use it instead of a qualified `then { … }`
 * escape hatch, so `it.<successor>` autocompletes exactly what follows the subprocess.
 */
fun <NEXT, M : FlowNode> ProcessPath<*>.exit(subprocess: Navigable<NEXT>, pick: (NEXT) -> M): ProcessPath<M> {
    val node = pick(subprocess.then())
    return ProcessPath(node, nodes + node)
}

/**
 * Walk a subprocess interior in a scoped block, then continue **on the subprocess node itself** — so the step
 * after the block is a plain, typed `then { it.continuation }` without naming the subprocess again. The block
 * starts positioned on the subprocess node (so it opens with `enter { it.start }`); its walked nodes are
 * recorded, and the current node afterwards is the subprocess (unchanged). Nesting works: each block captures
 * its own subprocess via the closure, so there is no lost-parent limitation.
 */
fun <N : FlowNode> ProcessPath<N>.inside(block: ProcessPath<N>.() -> ProcessPath<*>): ProcessPath<N> {
    val walked = ProcessPath(current, emptyList()).block()
    return ProcessPath(current, nodes + walked.nodes)
}

/**
 * Union of separately-walked branch paths into one unordered "was passed" set (deduplicated).
 */
fun passedNodes(vararg branches: List<FlowNode>): List<FlowNode> {
    return branches.flatMap { it }.distinct()
}
