package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition

/**
 * Sorts the flow nodes of **one BPMN scope** into process-flow order using DFS, so the JSON reads
 * top-to-bottom in execution order:
 *
 * - Start events are visited first (alphabetically)
 * - Boundary events are inserted after the node they are attached to, followed by their successors
 * - Cycles are handled by skipping already-visited nodes
 * - Any remaining unvisited nodes (e.g. isolated) are appended at the end, sorted alphabetically
 *
 * Sub-process children are not inlined — they live inside their sub-process node and are sorted by
 * applying this sorter to that scope in turn.
 */
internal object FlowNodeSorter {

    @Suppress("CyclomaticComplexMethod")
    fun sort(
        flowNodes: List<FlowNodeDefinition>,
        sequenceFlows: List<SequenceFlowDefinition>,
    ): List<FlowNodeDefinition> {
        val nodeById = flowNodes.associateBy { it.id }
        val targetsByFlowId = sequenceFlows.mapNotNull { flow -> flow.id?.let { it to flow.targetRef } }.toMap()
        val boundaryByHost = flowNodes
            .filterIsInstance<FlowNodeDefinition.Event>()
            .filter { it.attachedToRef != null }
            .groupBy { it.attachedToRef }
        val visited = mutableSetOf<String?>()
        val result = mutableListOf<FlowNodeDefinition>()

        fun successorsOf(node: FlowNodeDefinition): List<FlowNodeDefinition> {
            return node.outgoing
                .mapNotNull { targetsByFlowId[it] }
                .mapNotNull { nodeById[it] }
                .filter { it.id !in visited }
                .sortedBy { it.id ?: "" }
        }

        fun visit(node: FlowNodeDefinition) {
            if (node.id in visited) return
            visited.add(node.id)
            result.add(node)

            boundaryByHost[node.id]?.sortedBy { it.id ?: "" }?.forEach { boundary ->
                if (boundary.id !in visited) {
                    visited.add(boundary.id)
                    result.add(boundary)
                    successorsOf(boundary).forEach { visit(it) }
                }
            }

            successorsOf(node).filter { it.isNotBoundaryEvent() }.forEach { visit(it) }
        }

        val standalone = flowNodes.filter { it.isNotBoundaryEvent() }
        standalone.filter { it.isStartEvent() && it.incoming.isEmpty() }
            .sortedBy { it.id ?: "" }
            .forEach { visit(it) }
        standalone.filter { it.id !in visited }
            .sortedBy { it.id ?: "" }
            .forEach { visit(it) }

        return result
    }

    private fun FlowNodeDefinition.isNotBoundaryEvent(): Boolean {
        return (this as? FlowNodeDefinition.Event)?.shape != EventShape.BOUNDARY_EVENT
    }

    private fun FlowNodeDefinition.isStartEvent(): Boolean {
        return (this as? FlowNodeDefinition.Event)?.shape == EventShape.START_EVENT
    }
}
