package io.miragon.bpmn.runtime

/**
 * Base class for generated navigation nodes: it carries the element [id], [elementType] and display [name] so
 * each generated node doesn't repeat the [FlowNode] accessors. Nodes with successors additionally implement
 * [HasSuccessors] (their per-node `then()`, whose `Next` type differs per node, stays on the node itself).
 *
 * Identity is the element [id]: two nodes for the same element are equal, whatever their [name]. Kotlin's
 * generated nodes are `object` singletons (already reference-equal), but Java's generated nodes are fresh
 * instances per accessor, so id-based [equals]/[hashCode] keeps set operations (e.g. `nodesOf`
 * de-duplication) behaving the same in both languages.
 */
abstract class AbstractFlowNode @JvmOverloads constructor(
    override val id: ElementId,
    override val elementType: String,
    override val name: String? = null,
) : FlowNode {

    override fun equals(other: Any?): Boolean = this === other || (other is FlowNode && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
