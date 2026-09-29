package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that has structural successors, exposed behind [next].
 *
 * [NEXT] is the node's own successor holder (its generated `Next`), so `next` keeps per-node successor
 * typing — the compile-time edge check — while [HasSuccessors] adds a generic entry point on top.
 */
interface HasSuccessors<out NEXT> : FlowNode {
    val next: NEXT
}
