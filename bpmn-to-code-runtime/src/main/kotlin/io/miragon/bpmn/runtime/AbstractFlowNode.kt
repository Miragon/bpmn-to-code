package io.miragon.bpmn.runtime

/**
 * Base class for generated navigation nodes: it carries the element [id], [elementType] and display [name] so
 * each generated node doesn't repeat the [FlowNode] accessors. Nodes with successors additionally implement
 * [LeadsTo] (their per-node `outgoing`, typed to the node's own successor marker, stays on the node itself).
 *
 * Identity is the element [id]: two nodes for the same element are equal, whatever their [name]. Generated
 * nodes are singletons, but merged variants each generate their own node for a shared element, so id-based
 * [equals]/[hashCode] keeps set operations (e.g. `nodesOf` de-duplication) treating them as one.
 */
abstract class AbstractFlowNode @JvmOverloads constructor(
    override val id: ElementId,
    override val elementType: BpmnElementType,
    override val name: String? = null,
) : FlowNode {

    override fun equals(other: Any?): Boolean = this === other || (other is FlowNode && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "$elementType($id)"
}
