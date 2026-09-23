package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.shared.ElementTypeName
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessGraph

/**
 * Builds a typed navigation [FlowGraph] from a parsed [ProcessGraph].
 *
 * All required topology already lives on the graph: node-to-node adjacency (resolved through sequence flows),
 * boundary attachments, and subprocess containment (`parentIdOf`). No new extraction is needed — this factory
 * only reshapes that data into a nested, name-resolved graph.
 *
 * Scoping mirrors the containment tree: the root scope holds nodes with no parent; each subprocess node
 * carries its children as its own [FlowGraphNode.inner] scope, recursively. Sequence-flow continuation and
 * boundary edges are unified into one successor list, each edge named after the element it points to (one
 * naming rule for all edges). Call activities stay opaque (no descent into the called process); their called
 * process id is surfaced as pure info.
 */
object FlowGraphFactory {

    fun build(graph: ProcessGraph): FlowGraph {
        val childrenByParent = graph.allFlowNodes
            .mapNotNull { node -> node.id?.let { id -> FlowNodeWithId(id, node) } }
            .groupBy { graph.parentIdOf(it.id) }
        return buildScope(parentId = null, childrenByParent = childrenByParent, graph = graph)
    }

    private fun buildScope(
        parentId: String?,
        childrenByParent: Map<String?, List<FlowNodeWithId>>,
        graph: ProcessGraph,
    ): FlowGraph {
        val nodesInScope = childrenByParent[parentId] ?: emptyList()
        val names = FlowNaming.assignScope(nodesInScope)
        val navNodes = nodesInScope
            .sortedBy { names.getValue(it.id).objectName }
            .map { node -> buildNode(node, names, childrenByParent, graph) }
        return FlowGraph(navNodes)
    }

    private fun buildNode(
        node: FlowNodeWithId,
        scopeNames: Map<String, FlowNaming.Names>,
        childrenByParent: Map<String?, List<FlowNodeWithId>>,
        graph: ProcessGraph,
    ): FlowGraphNode {
        val definition = node.definition
        val names = scopeNames.getValue(node.id)
        return FlowGraphNode(
            objectName = names.objectName,
            propertyName = names.propertyName,
            id = node.id,
            elementType = ElementTypeName.of(definition),
            name = definition.displayName,
            isStart = definition.isStartEvent(),
            successors = buildSuccessors(definition, scopeNames, graph),
            inner = buildInner(node, childrenByParent, graph),
            calledProcessId = definition.calledProcessId(),
        )
    }

    /**
     * A subprocess node carries its children as its own [FlowGraphNode.inner] scope (recursively); every other
     * node has none. An empty subprocess collapses to `null` so it stays a plain leaf.
     */
    private fun buildInner(
        node: FlowNodeWithId,
        childrenByParent: Map<String?, List<FlowNodeWithId>>,
        graph: ProcessGraph,
    ): FlowGraph? = if (node.definition is FlowNodeDefinition.Activity.SubProcess) {
        buildScope(parentId = node.id, childrenByParent = childrenByParent, graph = graph)
            .takeIf { it.nodes.isNotEmpty() }
    } else {
        null
    }

    /**
     * Unifies sequence-flow successors and boundary edges into a single successor list. Only intra-scope
     * targets are linked (BPMN sequence flows and boundaries never cross a subprocess boundary); anything else
     * is skipped.
     */
    private fun buildSuccessors(
        node: FlowNodeDefinition,
        scopeNames: Map<String, FlowNaming.Names>,
        graph: ProcessGraph,
    ): List<FlowEdge> = (graph.followingElementsOf(node) + graph.attachedElementsOf(node))
        .distinct()
        .mapNotNull { targetId -> scopeNames[targetId] }
        .distinctBy { it.objectName }
        .sortedBy { it.propertyName }
        .map { FlowEdge(propertyName = it.propertyName, objectName = it.objectName) }

    private fun FlowNodeDefinition.isStartEvent(): Boolean = this is FlowNodeDefinition.Event && shape == EventShape.START_EVENT

    private fun FlowNodeDefinition.calledProcessId(): String? = (this as? FlowNodeDefinition.Activity.CallActivity)?.definition?.getValue()?.ifBlank { null }
}
