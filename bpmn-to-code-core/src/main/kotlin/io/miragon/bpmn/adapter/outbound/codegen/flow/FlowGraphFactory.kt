package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
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
 * the element it points to, while [FlowGraphNode.flows] keeps every sequence flow as its own typed edge.
 * Call activities stay opaque (no descent into the called process).
 */
object FlowGraphFactory {

    fun build(graph: ProcessGraph, definitions: RootElements): FlowGraph {
        val nodes = graph.allFlowNodes.mapNotNull { node -> node.id?.let { id -> FlowNodeWithId(id, node) } }
        val names = FlowNaming.assign(nodes)
        val facets = FlowFacetsFactory(names, definitions)
        val navNodes = nodes
            .sortedBy { names.getValue(it.id).objectName }
            .map { node -> buildNode(node, nodes, names, facets, graph) }
        return FlowGraph(navNodes)
    }

    private fun buildNode(
        node: FlowNodeWithId,
        allNodes: List<FlowNodeWithId>,
        names: Map<String, FlowNaming.Names>,
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
            name = definition.displayName,
            isStart = definition.isStartEvent(),
            successors = buildSuccessors(definition, names, graph),
            flows = buildFlows(definition, names, graph),
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
        names: Map<String, FlowNaming.Names>,
        graph: ProcessGraph,
    ): List<FlowEdge> {
        if (node.definition !is FlowNodeDefinition.Activity.SubProcess) return emptyList()
        return allNodes
            .filter { graph.parentIdOf(it.id) == node.id && it.definition.isStartEvent() }
            .map { names.getValue(it.id).toEdge() }
            .sortedBy { it.propertyName }
    }

    /**
     * Unifies sequence-flow successors and boundary edges into a single successor list.
     */
    private fun buildSuccessors(
        node: FlowNodeDefinition,
        names: Map<String, FlowNaming.Names>,
        graph: ProcessGraph,
    ): List<FlowEdge> = (graph.followingElementsOf(node) + graph.attachedElementsOf(node))
        .distinct()
        .mapNotNull { targetId -> names[targetId] }
        .distinctBy { it.objectName }
        .sortedBy { it.propertyName }
        .map { it.toEdge() }

    /**
     * One typed edge per outgoing sequence flow whose target is a known node; nothing is collapsed.
     */
    private fun buildFlows(
        node: FlowNodeDefinition,
        names: Map<String, FlowNaming.Names>,
        graph: ProcessGraph,
    ): List<SequenceFlowEdge> = graph.outgoingFlowsOf(node)
        .mapNotNull { flow -> names[flow.targetRef]?.let { target -> flow.toEdge(target) } }
        .sortedBy { it.propertyName }

    private fun SequenceFlowDefinition.toEdge(target: FlowNaming.Names): SequenceFlowEdge {
        val flowId = requireNotNull(id) { "a resolved sequence flow always has an id" }
        return SequenceFlowEdge(
            propertyName = FlowNaming.flowProperty(flowId),
            id = flowId,
            name = flowName,
            conditionExpression = conditionExpression,
            isDefault = isDefault,
            target = target.toEdge(),
        )
    }

    private fun FlowNaming.Names.toEdge(): FlowEdge = FlowEdge(propertyName = propertyName, objectName = objectName)

    private fun FlowNodeDefinition.isStartEvent(): Boolean = this is FlowNodeDefinition.Event && shape == EventShape.START_EVENT
}
