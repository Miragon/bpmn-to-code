package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind

/**
 * Sorts BPMN flow nodes in process-flow order using DFS:
 *
 * - Top-level start events are visited first (alphabetically)
 * - Subprocess children are inlined immediately after their parent subprocess
 * - Boundary events are inserted after the node they are attached to, followed by their successors
 * - Cycles are handled by skipping already-visited nodes
 * - Any remaining unvisited nodes (e.g. isolated) are appended at the end, sorted alphabetically
 */
object FlowNodeSorter {

    @Suppress("CyclomaticComplexMethod")
    fun sort(nodes: List<FlowNodeDefinition>): List<FlowNodeDefinition> {
        val nodeById = nodes.associateBy { it.id }
        val childrenByParent = nodes.filter { it.parentId != null }.groupBy { it.parentId }
        val boundaryByAttached = nodes.filter { it.attachedToRef != null }.groupBy { it.attachedToRef }
        val visited = mutableSetOf<String?>()
        val result = mutableListOf<FlowNodeDefinition>()

        fun visit(node: FlowNodeDefinition) {
            if (node.id in visited) return
            visited.add(node.id)
            result.add(node)

            if (node.nodeType.isSubProcess()) {
                val children = childrenByParent[node.id] ?: emptyList()
                val childStarts = children
                    .filter { it.nodeType.isStartEvent() && it.attachedToRef == null && it.previousElements.isEmpty() }
                    .sortedBy { it.id ?: "" }
                childStarts.forEach { visit(it) }
                children.filter { it.id !in visited && it.attachedToRef == null }
                    .sortedBy { it.id ?: "" }
                    .forEach { visit(it) }
            }

            val attached = boundaryByAttached[node.id]?.sortedBy { it.id ?: "" } ?: emptyList()
            for (boundary in attached) {
                if (boundary.id !in visited) {
                    visited.add(boundary.id)
                    result.add(boundary)
                    boundary.followingElements
                        .mapNotNull { nodeById[it] }
                        .filter { it.id !in visited }
                        .sortedBy { it.id ?: "" }
                        .forEach { visit(it) }
                }
            }

            node.followingElements
                .mapNotNull { nodeById[it] }
                .filter { it.id !in visited && it.attachedToRef == null }
                .sortedBy { it.id ?: "" }
                .forEach { visit(it) }
        }

        val topLevel = nodes.filter { it.parentId == null && it.attachedToRef == null }
        topLevel.filter { it.nodeType.isStartEvent() && it.previousElements.isEmpty() }
            .sortedBy { it.id ?: "" }
            .forEach { visit(it) }
        topLevel.filter { it.id !in visited }
            .sortedBy { it.id ?: "" }
            .forEach { visit(it) }

        return result
    }

    private fun BpmnNodeType.isSubProcess(): Boolean =
        this is BpmnNodeType.Activity.SubProcess && kind == SubProcessKind.PLAIN

    private fun BpmnNodeType.isStartEvent(): Boolean =
        this is BpmnNodeType.Event && shape == EventShape.START_EVENT
}
