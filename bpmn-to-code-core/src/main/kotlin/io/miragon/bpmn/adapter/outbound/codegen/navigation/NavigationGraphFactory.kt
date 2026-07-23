package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph.NavigationEdge
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph.NavigationNode
import io.miragon.bpmn.adapter.outbound.shared.ElementTypeName
import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties

/**
 * Builds a typed navigation [NavigationGraph] from parsed flow nodes.
 *
 * All required topology already lives on [FlowNodeDefinition]: `followingElements` (sequence-flow
 * successors), `attachedElements` (boundary edges), and `parentId` (subprocess containment). No new
 * extraction is needed — this factory only reshapes that data into a nested, name-resolved graph.
 *
 * Scoping mirrors the containment tree: the root scope holds nodes with `parentId == null`; each
 * subprocess node carries its children (`parentId == subprocessId`) as its own [NavigationNode.inner] scope,
 * recursively. Sequence-flow continuation and boundary edges are unified into one successor list, each
 * edge named after the element it points to (one naming rule for all edges). Call activities stay opaque
 * (no descent into the called process); their called process id is surfaced as pure info.
 */
object NavigationGraphFactory {

    fun build(flowNodes: List<FlowNodeDefinition>): NavigationGraph {
        val childrenByParent = flowNodes
            .filter { it.id != null }
            .groupBy { it.parentId }
        return buildScope(parentId = null, childrenByParent = childrenByParent)
    }

    private fun buildScope(
        parentId: String?,
        childrenByParent: Map<String?, List<FlowNodeDefinition>>,
    ): NavigationGraph {
        val nodesInScope = childrenByParent[parentId] ?: emptyList()
        val names = NavigationNaming.assignScope(nodesInScope)
        val navNodes = nodesInScope
            .sortedBy { names.getValue(it.id!!).objectName }
            .map { node -> buildNode(node, names, childrenByParent) }
        return NavigationGraph(navNodes)
    }

    private fun buildNode(
        node: FlowNodeDefinition,
        scopeNames: Map<String, NavigationNaming.Names>,
        childrenByParent: Map<String?, List<FlowNodeDefinition>>,
    ): NavigationNode {
        val names = scopeNames.getValue(node.id!!)
        return NavigationNode(
            objectName = names.objectName,
            propertyName = names.propertyName,
            id = node.id,
            elementType = ElementTypeName.of(node.nodeType),
            name = node.displayName,
            isStart = node.isStartEvent(),
            successors = buildSuccessors(node, scopeNames),
            inner = buildInner(node, childrenByParent),
            calledProcessId = node.calledProcessId(),
        )
    }

    /**
     * A subprocess node carries its children as its own [NavigationNode.inner] scope (recursively); every other node
     * has none. An empty subprocess collapses to `null` so it stays a plain leaf.
     */
    private fun buildInner(
        node: FlowNodeDefinition,
        childrenByParent: Map<String?, List<FlowNodeDefinition>>,
    ): NavigationGraph? {
        return if (node.nodeType is BpmnNodeType.Activity.SubProcess) {
            buildScope(parentId = node.id, childrenByParent = childrenByParent).takeIf { it.nodes.isNotEmpty() }
        } else {
            null
        }
    }

    /**
     * Unifies sequence-flow successors (`followingElements`) and boundary edges (`attachedElements`) into a
     * single successor list. Only intra-scope targets are linked (BPMN sequence flows and boundaries never
     * cross a subprocess boundary); anything else is skipped.
     */
    private fun buildSuccessors(
        node: FlowNodeDefinition,
        scopeNames: Map<String, NavigationNaming.Names>,
    ): List<NavigationEdge> {
        return (node.followingElements + node.attachedElements)
            .distinct()
            .mapNotNull { targetId -> scopeNames[targetId] }
            .distinctBy { it.objectName }
            .sortedBy { it.propertyName }
            .map { NavigationEdge(propertyName = it.propertyName, objectName = it.objectName) }
    }

    private fun FlowNodeDefinition.isStartEvent(): Boolean {
        val type = nodeType
        return type is BpmnNodeType.Event && type.shape == EventShape.START_EVENT
    }

    private fun FlowNodeDefinition.calledProcessId(): String? {
        val props = properties
        return if (props is FlowNodeProperties.CallActivity) {
            props.definition.getValue().ifBlank { null }
        } else {
            null
        }
    }
}
