package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that has structural successors, exposed behind [then].
 *
 * [NEXT] is the node's own successor holder (its generated `Next`), so `then()` keeps per-node successor
 * typing — the compile-time edge check — while [Navigable] adds a generic entry point on top.
 */
interface Navigable<out NEXT> : FlowNode {
    fun then(): NEXT
}
