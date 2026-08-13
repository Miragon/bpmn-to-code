package io.miragon.bpmn.adapter.outbound.codegen.navigation

/**
 * Language-agnostic intermediate representation of a process as a typed navigation graph.
 *
 * It is computed once by [NavigationGraphFactory] and rendered per output language by the API builders
 * Nodes reference their successors by name (not by object reference), which keeps the structure cycle-safe.
 */
data class NavigationGraph(
    val nodes: List<NavigationNode>,
) {

    /**
     * A single element in the navigation graph.
     *
     * @property objectName PascalCase identifier, unique within its scope — becomes the generated nested object/class.
     * @property propertyName camelCase accessor for this node; also the name predecessors use to reach it.
     * @property id the raw BPMN element id, wrapped as `ElementId(id)` in the generated `.id`.
     * @property elementType the flat `elementType` string (see `ElementTypeName`).
     * @property name the element's display name, or `null` when the model declares none.
     * @property isStart whether this node is a start event of its scope (an entry point for the hook twin).
     * @property successors the reachable next elements — sequence-flow continuation and boundary edges unified,
     *   each named after the element it points to.
     * @property inner the subprocess interior as its own scope (the only wrapper); `null` for non-subprocess nodes.
     * @property calledProcessId for call activities, the called process id as pure info; `null` otherwise.
     */
    data class NavigationNode(
        val objectName: String,
        val propertyName: String,
        val id: String,
        val elementType: String,
        val name: String?,
        val isStart: Boolean,
        val successors: List<NavigationEdge>,
        val inner: NavigationGraph?,
        val calledProcessId: String?,
    )

    /**
     * A directed edge to a reachable successor, named after the target element.
     *
     * @property propertyName the target's camelCase [NavigationNode.propertyName] — the property emitted on the source node.
     * @property objectName the target's PascalCase [NavigationNode.objectName] — what the getter returns.
     */
    data class NavigationEdge(
        val propertyName: String,
        val objectName: String,
    )
}
