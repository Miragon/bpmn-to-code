package io.miragon.bpmn.adapter.outbound.codegen.navigation

/**
 * Language-agnostic intermediate representation of a process as a typed navigation graph.
 *
 * A [NavGraph] is a single navigation scope (the process itself, or the interior of a subprocess).
 * It is computed once by [NavigationGraphFactory] and rendered per output language by the Process API
 * builders, and reused by the optional hook twin — so navigation names stay identical across all of them.
 *
 * Nodes reference their successors by name (not by object reference), which keeps the structure cycle-safe
 * (BPMN loops) and lets the renderers emit forward references via computed getters.
 *
 * [NavNode] and [NavEdge] are nested here (one top-level type per file) since they only exist as part of a graph.
 */
data class NavGraph(
    val nodes: List<NavNode>,
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
    data class NavNode(
        val objectName: String,
        val propertyName: String,
        val id: String,
        val elementType: String,
        val name: String?,
        val isStart: Boolean,
        val successors: List<NavEdge>,
        val inner: NavGraph?,
        val calledProcessId: String?,
    )

    /**
     * A directed edge to a reachable successor, named after the target element.
     *
     * @property propertyName the target's camelCase [NavNode.propertyName] — the property emitted on the source node.
     * @property objectName the target's PascalCase [NavNode.objectName] — what the getter returns.
     */
    data class NavEdge(
        val propertyName: String,
        val objectName: String,
    )
}
