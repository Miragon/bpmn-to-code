package io.miragon.bpmn.adapter.outbound.codegen.flow

/**
 * Language-agnostic intermediate representation of a process as a typed navigation graph.
 *
 * It is computed once by [FlowGraphFactory] and rendered per output language by the API builders
 * Nodes reference their successors by name (not by object reference), which keeps the structure cycle-safe.
 */
data class FlowGraph(
    val nodes: List<FlowGraphNode>,
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
    data class FlowGraphNode(
        val objectName: String,
        val propertyName: String,
        val id: String,
        val elementType: String,
        val name: String?,
        val isStart: Boolean,
        val successors: List<FlowEdge>,
        val inner: FlowGraph?,
        val calledProcessId: String?,
    )

    /**
     * A directed edge to a reachable successor, named after the target element.
     *
     * @property propertyName the target's camelCase [FlowGraphNode.propertyName] — the property emitted on the source node.
     * @property objectName the target's PascalCase [FlowGraphNode.objectName] — what the getter returns.
     */
    data class FlowEdge(
        val propertyName: String,
        val objectName: String,
    )
}
