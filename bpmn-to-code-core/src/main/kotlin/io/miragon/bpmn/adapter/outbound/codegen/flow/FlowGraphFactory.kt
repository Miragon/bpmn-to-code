package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
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
 * boundary attachments, and subprocess containment (`parentIdOf`). No new extraction is needed — this factory
 * only reshapes that data into a flat, name-resolved graph.
 *
 * Every node of the tree becomes a top-level entry, so a node is addressed by its own name regardless of the
 * subprocess it sits in; names are unique model-wide, guaranteed by the mandatory `collision-detection` rule.
 * A subprocess keeps its scope only as [FlowGraphNode.interiorStarts]: the start events directly inside it.
 * Sequence-flow continuation and boundary edges are unified into one successor list, each edge named after
 * the element it points to, while [FlowGraphNode.outgoingFlows] keeps every sequence flow, grouped by its target.
 * Call activities stay opaque (no descent into the called process).
 */
object FlowGraphFactory {

    fun build(graph: ProcessGraph, definitions: RootElements): FlowGraph {
        val nodes = graph.allFlowNodes.mapNotNull { node -> node.id?.let { id -> FlowNodeWithId(id, node) } }
        val names = namesMarkingBranches(nodes, graph)
        val facets = FlowFacetsFactory(names, definitions)
        val successorsById = nodes.associate { it.id to buildSuccessors(node = it.definition, names = names, graph = graph) }
        val navNodes = nodes
            .sortedBy { names.getValue(it.id).objectName }
            .map { node -> buildNode(node = node, allNodes = nodes, names = names, successorsById = successorsById, facets = facets, graph = graph) }
        return FlowGraph(navNodes)
    }

    private fun namesMarkingBranches(nodes: List<FlowNodeWithId>, graph: ProcessGraph): Map<String, FlowEdge> {
        val names = FlowNaming.assign(nodes)
        return nodes.associate { node ->
            val successors = buildSuccessors(node = node.definition, names = names, graph = graph)
            node.id to names.getValue(node.id).copy(branches = successors.size > 1)
        }
    }

    private fun buildNode(
        node: FlowNodeWithId,
        allNodes: List<FlowNodeWithId>,
        names: Map<String, FlowEdge>,
        successorsById: Map<String, List<FlowEdge>>,
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
            successors = successorsById.getValue(node.id),
            predecessors = buildPredecessors(node = ownNames, names = names, successorsById = successorsById),
            outgoingFlows = buildOutgoingFlows(node = definition, names = names, graph = graph),
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
     * Unifies sequence-flow successors and boundary edges into a single successor list.
     */
    private fun buildSuccessors(
        node: FlowNodeDefinition,
        names: Map<String, FlowEdge>,
        graph: ProcessGraph,
    ): List<FlowEdge> = (graph.followingElementsOf(node) + graph.attachedElementsOf(node))
        .distinct()
        .mapNotNull { targetId -> names[targetId] }
        .distinctBy { it.objectName }.sortedBy { it.propertyName }

    private fun buildPredecessors(
        node: FlowEdge,
        names: Map<String, FlowEdge>,
        successorsById: Map<String, List<FlowEdge>>,
    ): List<FlowEdge> {
        val predecessorIds = successorsById.filterValues { successors -> node in successors }.keys
        return predecessorIds.map { names.getValue(it) }.sortedBy { it.propertyName }
    }

    /**
     * The outgoing sequence flows whose target is a known node, grouped by that target; no flow is dropped.
     */
    private fun buildOutgoingFlows(
        node: FlowNodeDefinition,
        names: Map<String, FlowEdge>,
        graph: ProcessGraph,
    ): List<FlowsToTarget> = graph.outgoingFlowsOf(node)
        .mapNotNull { flow -> names[flow.targetRef]?.let { target -> target to flow.toEdge() } }
        .groupBy({ (target, _) -> target }, { (_, flow) -> flow })
        .map { (target, flows) -> FlowsToTarget(target = target, flows = flows.sortedBy { it.id }) }
        .sortedBy { it.target.propertyName }

    private fun SequenceFlowDefinition.toEdge(): SequenceFlowEdge = SequenceFlowEdge(
        id = requireNotNull(id) { "a resolved sequence flow always has an id" },
        name = flowName,
        conditionExpression = conditionExpression,
        isDefault = isDefault,
    )

    private fun FlowNodeDefinition.isStartEvent(): Boolean = this is FlowNodeDefinition.Event && shape == EventShape.START_EVENT
}
