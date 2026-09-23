package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that contains flow of its own — a subprocess — and opens it via [start], which yields the
 * interior's start element(s). A subprocess node is both [HasSuccessors] (`then()` = what follows it) and
 * [FlowScope] (`start()` = what its interior begins with), so tooling can continue past it or descend into it.
 */
interface FlowScope<out START> : FlowNode {
    fun start(): START
}
