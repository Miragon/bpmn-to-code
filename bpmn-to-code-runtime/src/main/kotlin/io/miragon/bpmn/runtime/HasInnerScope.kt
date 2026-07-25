package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that contains a nested navigation scope — a subprocess interior — reachable via [inner].
 *
 * [INNER] is the interior scope, itself a [HasSuccessors] whose `then()` yields the scope's start event(s). A
 * subprocess node is therefore both [HasSuccessors] (its `then()` = what follows the subprocess) and [HasInnerScope]
 * (its `inner()` = the interior), so tooling can continue past it or descend into it, both type-safely.
 */
interface HasInnerScope<out INNER> : FlowNode {
    fun inner(): INNER
}
