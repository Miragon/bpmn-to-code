package io.miragon.bpmn.runtime

/**
 * Base class for generated navigation nodes: it carries the element [id] and [elementType] so each generated
 * node doesn't repeat the [FlowNode] accessors. Nodes with successors additionally implement [Navigable]
 * (their per-node `then()`, whose `Next` type differs per node, stays on the node itself).
 */
abstract class AbstractFlowNode(
    override val id: ElementId,
    override val elementType: String,
) : FlowNode
