package io.miragon.bpmn.domain.shared

/**
 * Derived views over a scope tree of [FlowNodeDefinition]s.
 *
 * The tree is the store — a sub-process owns its children and its own sequence flows — while merging,
 * validation, collision detection and code generation all reason over a flat node set. This class
 * provides that projection, plus the node-to-node adjacency that [FlowNodeDefinition.incoming] /
 * [FlowNodeDefinition.outgoing] express as sequence-flow references.
 */
class ProcessGraph(
    private val flowNodes: List<FlowNodeDefinition>,
    private val sequenceFlows: List<SequenceFlowDefinition>,
) {

    /**
     * Every node in the tree, depth-first, each container immediately followed by its children.
     */
    val allFlowNodes: List<FlowNodeDefinition> by lazy { flatten(flowNodes) }

    /**
     * Every sequence flow in the tree, the root scope's first, then each sub-process scope's.
     */
    val allSequenceFlows: List<SequenceFlowDefinition> by lazy { sequenceFlows + nestedFlows(flowNodes) }

    private val flowById: Map<String, SequenceFlowDefinition> by lazy {
        allSequenceFlows.mapNotNull { flow -> flow.id?.let { it to flow } }.toMap()
    }

    private val parentIdByNodeId: Map<String, String> by lazy { buildParentIndex(flowNodes, null) }

    fun parentIdOf(nodeId: String?): String? = nodeId?.let { parentIdByNodeId[it] }

    /**
     * Ids of the flow nodes that precede [node], resolved through its incoming sequence flows.
     */
    fun previousElementsOf(node: FlowNodeDefinition): List<String> {
        return node.incoming.mapNotNull { flowById[it]?.sourceRef }
    }

    /**
     * Ids of the flow nodes that follow [node], resolved through its outgoing sequence flows.
     */
    fun followingElementsOf(node: FlowNodeDefinition): List<String> {
        return node.outgoing.mapNotNull { flowById[it]?.targetRef }
    }

    /**
     * Boundary events attached to [node]; empty for anything that is not an activity.
     */
    fun attachedElementsOf(node: FlowNodeDefinition): List<String> {
        return (node as? FlowNodeDefinition.Activity)?.boundaryEventRefs ?: emptyList()
    }

    private fun flatten(nodes: List<FlowNodeDefinition>): List<FlowNodeDefinition> {
        return nodes.flatMap { node ->
            if (node is FlowNodeDefinition.Activity.SubProcess) {
                listOf(node) + flatten(node.flowNodes)
            } else {
                listOf(node)
            }
        }
    }

    private fun nestedFlows(nodes: List<FlowNodeDefinition>): List<SequenceFlowDefinition> {
        return nodes.filterIsInstance<FlowNodeDefinition.Activity.SubProcess>()
            .flatMap { it.sequenceFlows + nestedFlows(it.flowNodes) }
    }

    private fun buildParentIndex(
        nodes: List<FlowNodeDefinition>,
        parentId: String?,
    ): Map<String, String> {
        return buildMap {
            nodes.forEach { node ->
                if (parentId != null && node.id != null) put(node.id!!, parentId)
                if (node is FlowNodeDefinition.Activity.SubProcess) {
                    putAll(buildParentIndex(node.flowNodes, node.id))
                }
            }
        }
    }
}
