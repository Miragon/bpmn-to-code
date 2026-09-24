package io.miragon.bpmn.adapter.outbound.codegen.flow

/**
 * Language-agnostic intermediate representation of a process as a typed navigation graph.
 *
 * It is computed once by [FlowGraphFactory] and rendered per output language by the API builders.
 * The graph is flat: every node of the process, whatever its subprocess depth, is a direct entry of [nodes],
 * and a subprocess only points at its interior's start elements. Nodes reference each other by name
 * (not by object reference), which keeps the structure cycle-safe.
 */
data class FlowGraph(
    val nodes: List<FlowGraphNode>,
) {

    /**
     * A single element in the navigation graph.
     *
     * @property objectName PascalCase identifier, unique model-wide — becomes the generated nested object/class.
     * @property propertyName camelCase accessor for this node; also the name predecessors use to reach it.
     * @property id the raw BPMN element id, wrapped as `ElementId(id)` in the generated `.id`.
     * @property elementType the flat `elementType` string (see `ElementTypeName`).
     * @property name the element's display name, or `null` when the model declares none.
     * @property isStart whether this node is a start event of its scope.
     * @property successors the reachable next elements — sequence-flow continuation and boundary edges unified,
     *   each named after the element it points to.
     * @property flows the outgoing sequence flows as typed edges, one per flow — parallel flows to the same
     *   target stay separate here, unlike in [successors].
     * @property interiorStarts for a subprocess, the start events directly inside it; empty for every other node
     *   and for a subprocess without a start event.
     * @property facets the element's own data (job type, variables, timer, …), mirroring the BPMN subtype.
     */
    data class FlowGraphNode(
        val objectName: String,
        val propertyName: String,
        val id: String,
        val elementType: String,
        val name: String?,
        val isStart: Boolean,
        val successors: List<FlowEdge>,
        val flows: List<SequenceFlowEdge>,
        val interiorStarts: List<FlowEdge>,
        val facets: NodeFacets,
    )

    /**
     * A directed edge to a reachable node, named after the target element.
     *
     * @property propertyName the target's camelCase [FlowGraphNode.propertyName] — the property emitted on the source node.
     * @property objectName the target's PascalCase [FlowGraphNode.objectName] — what the getter returns.
     */
    data class FlowEdge(
        val propertyName: String,
        val objectName: String,
    )

    /**
     * One outgoing `bpmn:sequenceFlow`, named after its own id.
     *
     * @property propertyName camelCase of the flow id — the property emitted in the source node's `Flows` holder.
     * @property conditionExpression the raw expression text, or `null` for an unconditional flow.
     */
    data class SequenceFlowEdge(
        val propertyName: String,
        val id: String,
        val name: String?,
        val conditionExpression: String?,
        val isDefault: Boolean,
        val target: FlowEdge,
    )

    /**
     * The element's own data, each entry present only when the BPMN subtype carries it.
     *
     * @property attachedTo for a boundary event, the node it is attached to.
     * @property isInterrupting for a boundary event whether it cancels its host; for an event-subprocess start
     *   event whether it interrupts the parent scope.
     */
    data class NodeFacets(
        val jobType: String? = null,
        val variables: List<VariableFacet> = emptyList(),
        val calledProcessId: String? = null,
        val inputs: List<MappingFacet> = emptyList(),
        val outputs: List<MappingFacet> = emptyList(),
        val timer: TimerFacet? = null,
        val message: String? = null,
        val signal: String? = null,
        val error: NamedCode? = null,
        val escalation: NamedCode? = null,
        val attachedTo: FlowEdge? = null,
        val isInterrupting: Boolean? = null,
    )

    data class VariableFacet(
        val constantName: String,
        val rawName: String,
        val subtype: VariableNameSubtype,
    )

    data class MappingFacet(
        val constantName: String,
        val target: String,
        val source: String?,
        val sourceExpression: String?,
    )

    data class TimerFacet(
        val type: String,
        val expression: String,
    )

    data class NamedCode(
        val name: String,
        val code: String,
    )
}
