package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that contains flow of its own — a subprocess — and opens it via [startEvents], which yields the
 * interior's start element(s). A subprocess node is both [LeadsTo] (`outgoing` = what follows it) and
 * [FlowScope] (`startEvents` = what its interior begins with), so tooling can continue past it or descend into it.
 */
interface FlowScope<out START> : FlowNode {
    val startEvents: START
}
