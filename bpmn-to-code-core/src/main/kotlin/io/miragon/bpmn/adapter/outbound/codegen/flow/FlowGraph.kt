package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.domain.shared.TimerType

/**
 * Language-agnostic intermediate representation of a process as a typed navigation graph.
 *
 * It is computed once by [FlowGraphFactory] and rendered per output language by the API builders.
 * The graph is flat: every node of the process, whatever its subprocess depth, is a direct entry of [nodes],
 * and a subprocess only points at its interior's start elements. Nodes reference each other by name
 * (not by object reference), which keeps the structure cycle-safe.
 */
data class FlowGraph(val nodes: List<FlowGraphNode>) {

    /**
     * A single element in the navigation graph.
     *
     * @property objectName PascalCase identifier, unique model-wide — becomes the generated nested object/class.
     * @property propertyName camelCase accessor for this node; also the name predecessors use to reach it.
     * @property id the raw BPMN element id, wrapped as `ElementId(id)` in the generated `.id`.
     * @property elementType the flat `elementType` string (see `ElementTypeName`).
     * @property name the element's display name, or `null` when the model declares none.
     * @property isStart whether this node is a start event of its scope.
     * @property eventType the event's `BpmnEventType` constant name; `null` for every non-event node.
     * @property isBoundaryEvent whether this node is a boundary event, reached from its host without a sequence flow.
     * @property successors the reachable next elements — sequence-flow continuation and boundary edges unified,
     *   each named after the element it points to.
     * @property outgoingFlows the outgoing sequence flows, grouped by the element they lead to and named after it;
     *   boundary edges are not sequence flows and never appear here.
     * @property interiorStarts for a subprocess, the start events directly inside it; empty for every other node
     *   and for a subprocess without a start event.
     * @property facets the element's own data (job type, variables, timer, …), mirroring the BPMN subtype.
     */
    data class FlowGraphNode(
        val objectName: String,
        val propertyName: String,
        val id: String,
        val elementType: String,
        val eventType: String?,
        val name: String?,
        val isStart: Boolean,
        val isBoundaryEvent: Boolean,
        val successors: List<FlowEdge>,
        val outgoingFlows: List<FlowsToTarget>,
        val interiorStarts: List<FlowEdge>,
        val facets: NodeFacets,
    )

    /**
     * A directed edge to a reachable node, named after the target element.
     *
     * @property propertyName the target's camelCase [FlowGraphNode.propertyName] — the property emitted on the source node.
     * @property objectName the target's PascalCase [FlowGraphNode.objectName] — what the getter returns.
     */
    data class FlowEdge(val propertyName: String, val objectName: String)

    /**
     * The outgoing sequence flows of a node that lead to the same element.
     *
     * @property propertyName `to` + the target's object name — the property emitted in the node's `OutgoingFlows`.
     * @property flows usually exactly one; several when more than one sequence flow leads to [target], which the
     *   generated property then exposes as a list under the same name.
     */
    data class FlowsToTarget(val propertyName: String, val target: FlowEdge, val flows: List<SequenceFlowEdge>)

    /**
     * One outgoing `bpmn:sequenceFlow`.
     *
     * @property conditionExpression the raw expression text, or `null` for an unconditional flow.
     */
    data class SequenceFlowEdge(
        val id: String,
        val name: String?,
        val conditionExpression: String?,
        val isDefault: Boolean,
    ) {
        fun hasOnlyDefaults(): Boolean = name == null && conditionExpression == null && !isDefault
    }

    /**
     * The element's own data, each entry present only when the BPMN subtype carries it.
     *
     * @property attachedTo for a boundary event, the node it is attached to.
     * @property isInterrupting for a boundary event whether it cancels its host; for an event-subprocess start
     *   event whether it interrupts the parent scope.
     */
    data class NodeFacets(
        val jobType: SharedValue<String>? = null,
        val variables: List<VariableFacet> = emptyList(),
        val calledProcessId: String? = null,
        val inputs: List<MappingFacet> = emptyList(),
        val outputs: List<MappingFacet> = emptyList(),
        val timer: TimerFacet? = null,
        val message: SharedValue<String>? = null,
        val signal: SharedValue<String>? = null,
        val error: SharedValue<NamedCode>? = null,
        val escalation: SharedValue<NamedCode>? = null,
        val attachedTo: FlowEdge? = null,
        val isInterrupting: Boolean? = null,
    )

    data class VariableFacet(val constantName: String, val rawName: String, val subtype: VariableNameSubtype)

    data class MappingFacet(
        val constantName: String,
        val target: String,
        val source: String?,
        val sourceExpression: String?,
    )

    data class TimerFacet(val type: TimerType, val expression: String)

    data class NamedCode(val name: String, val code: String)

    /**
     * A node's job type, message, signal, error or escalation. [constant] names the shared definition holding
     * [value]; it is `null` when no root element of the model declares the value, as then no shared constant
     * exists and the node has to carry the value itself.
     */
    data class SharedValue<T>(val value: T, val constant: SharedConstant?)

    /**
     * A constant of the shared definition files, as Kotlin and Java declare it and the C# member name is derived from.
     */
    data class SharedConstant(val name: String)
}
