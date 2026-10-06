package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge
import io.miragon.bpmn.adapter.outbound.shared.ElementTypeName
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessGraph
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition

/**
 * Builds a typed navigation [FlowGraph] from a parsed [ProcessGraph].
 *
 * All required topology already lives on the graph: node-to-node adjacency (resolved through sequence flows),
 * boundary attachments, compensation handlers, and subprocess containment (`parentIdOf`). This factory only
 * reshapes that data into a flat, name-resolved graph.
 *
 * Every node of the tree becomes a top-level entry, so a node is addressed by its own name regardless of the
 * subprocess it sits in; names are unique model-wide, guaranteed by the mandatory `collision-detection` rule.
 * A subprocess keeps its scope only as [FlowGraphNode.interiorStarts]: the start events directly inside it.
 * Sequence-flow continuation, boundary edges and compensation handlers are unified into one successor list, each
 * edge named after the element it points to and knowing how it gets there; no sequence flow is dropped.
 * Call activities stay opaque (no descent into the called process).
 */
object FlowGraphFactory {

    fun build(graph: ProcessGraph, definitions: RootElements): FlowGraph {
        val nodes = graph.allFlowNodes.mapNotNull { node -> node.id?.let { id -> FlowNodeWithId(id, node) } }
        val names = FlowNaming.assign(nodes)
        val facets = FlowFacetsFactory(names, definitions)
        val navNodes = nodes
            .sortedBy { names.getValue(it.id).objectName }.map { node -> buildNode(node = node, allNodes = nodes, names = names, facets = facets, graph = graph) }
        return FlowGraph(navNodes)
    }

    private fun buildNode(
        node: FlowNodeWithId,
        allNodes: List<FlowNodeWithId>,
        names: Map<String, FlowEdge>,
        facets: FlowFacetsFactory,
        graph: ProcessGraph,
    ): FlowGraphNode {
        val definition = node.definition
        val ownNames = names.getValue(node.id)
        return FlowGraphNode(
            objectName = ownNames.objectName,
            propertyName = ownNames.propertyName,
            id = node.id,
            elementType = ElementTypeName.of(definition),
            eventType = (definition as? FlowNodeDefinition.Event)?.let { ElementTypeName.eventTypeOf(it) },
            name = definition.displayName,
            isBoundaryEvent = definition is FlowNodeDefinition.Event && definition.shape == EventShape.BOUNDARY_EVENT,
            successors = buildSuccessors(node = definition, names = names, graph = graph),
            interiorStarts = buildInteriorStarts(node, allNodes, names, graph),
            facets = facets.of(definition),
        )
    }

    /**
     * A subprocess points at the start events directly inside it; nested subprocesses list their own.
     */
    private fun buildInteriorStarts(
        node: FlowNodeWithId,
        allNodes: List<FlowNodeWithId>,
        names: Map<String, FlowEdge>,
        graph: ProcessGraph,
    ): List<FlowEdge> {
        if (node.definition !is FlowNodeDefinition.Activity.SubProcess) return emptyList()
        return allNodes
            .filter { graph.parentIdOf(it.id) == node.id && it.definition.isStartEvent() }
            .map { names.getValue(it.id) }.sortedBy { it.propertyName }
    }

    /**
     * Unifies sequence-flow successors, boundary edges and the compensation handler into a single successor list.
     * A target reached in several ways is listed once: by its sequence flows first, as a boundary event second.
     */
    private fun buildSuccessors(
        node: FlowNodeDefinition,
        names: Map<String, FlowEdge>,
        graph: ProcessGraph,
    ): List<SuccessorEdge> {
        val boundaryEvents = graph.attachedElementsOf(node)
            .mapNotNull { targetId -> names[targetId] }.map { SuccessorEdge.AttachedBoundaryEvent(it) }
        val compensationHandler = listOfNotNull(graph.compensationHandlerOf(node))
            .mapNotNull { targetId -> names[targetId] }.map { SuccessorEdge.AssociatedCompensationHandler(it) }
        val successors = buildSequenceFlowSuccessors(node = node, names = names, graph = graph) + boundaryEvents + compensationHandler
        return successors.distinctBy { it.target.objectName }.sortedBy { it.target.propertyName }
    }

    /**
     * The outgoing sequence flows whose target is a known node, grouped by that target; no flow is dropped.
     */
    private fun buildSequenceFlowSuccessors(
        node: FlowNodeDefinition,
        names: Map<String, FlowEdge>,
        graph: ProcessGraph,
    ): List<SuccessorEdge.ViaSequenceFlows> = graph.outgoingFlowsOf(node)
        .mapNotNull { flow -> names[flow.targetRef]?.let { target -> target to flow.toEdge() } }
        .groupBy({ (target, _) -> target }, { (_, flow) -> flow })
        .map { (target, flows) -> SuccessorEdge.ViaSequenceFlows(target = target, flows = flows.sortedBy { it.id }) }

    private fun SequenceFlowDefinition.toEdge(): SequenceFlowEdge = SequenceFlowEdge(
        id = requireNotNull(id) { "a resolved sequence flow always has an id" },
        name = flowName,
        conditionExpression = conditionExpression,
        isDefault = isDefault,
    )

    private fun FlowNodeDefinition.isStartEvent(): Boolean = this is FlowNodeDefinition.Event && shape == EventShape.START_EVENT
}
