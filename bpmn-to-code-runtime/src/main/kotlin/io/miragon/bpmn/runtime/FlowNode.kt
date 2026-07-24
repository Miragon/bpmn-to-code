package io.miragon.bpmn.runtime

/**
 * Common supertype of every node in a generated `Relations` navigation graph.
 *
 * Gives generic tooling (path builders, drift contracts, assertions) a shared handle on a flow element —
 * its [id] and flat [elementType] — without knowing the concrete generated node type.
 * Terminal elements (e.g. end events) implement [FlowNode] only; nodes with successors implement [Navigable].
 */
interface FlowNode {
    val id: ElementId
    val elementType: String
}
