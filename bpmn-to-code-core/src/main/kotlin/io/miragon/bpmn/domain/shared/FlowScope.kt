package io.miragon.bpmn.domain.shared

/**
 * The flow nodes and sequence flows owned by one BPMN scope.
 *
 * BPMN calls this a `bpmn:FlowElementsContainer`: a process and a sub-process are containers in exactly
 * the same sense, each owning its children *and* the flows between them. Naming it once is what lets the
 * process model and [FlowNodeDefinition.Activity.SubProcess] share the concept instead of
 * each carrying the two lists apart — and what lets a reader hand back one value rather than a pair.
 *
 * This is the store. [ProcessGraph] is the flattened projection over it.
 */
data class FlowScope(
    val flowNodes: List<FlowNodeDefinition> = emptyList(),
    val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
) {

    /**
     * Keeps the first node and the first sequence flow of each id, in this scope and in every scope nested inside it.
     * A node without an id cannot be referenced and is dropped; what a node lists twice is listed once.
     */
    fun deduplicated(): FlowScope {
        val identifiableNodes = flowNodes.filter { !it.id.isNullOrEmpty() }
        val distinctNodes = identifiableNodes.distinctBy { it.id }
            .map { node -> if (node is FlowNodeDefinition.Activity.SubProcess) node.withScope(node.scope().deduplicated()) else node }
            .map { it.withoutRepeatedEntries() }
        return FlowScope(distinctNodes, sequenceFlows.distinctBy { it.id.orEmpty() })
    }

    /**
     * Sorts this scope and every scope nested inside it, so generated output is a function of the model
     * rather than of the order the files happened to be read in.
     */
    fun sorted(): FlowScope {
        val sortedNodes = flowNodes
            .map { node -> if (node is FlowNodeDefinition.Activity.SubProcess) node.withScope(node.scope().sorted()) else node }
            .sortedBy { it.id.orEmpty() }
        return FlowScope(sortedNodes, sequenceFlows.sortedBy { it.id.orEmpty() })
    }

    private fun FlowNodeDefinition.withoutRepeatedEntries(): FlowNodeDefinition {
        val distinctVariables = variables.distinct()
        return when (this) {
            is FlowNodeDefinition.Gateway -> copy(variables = distinctVariables)
            is FlowNodeDefinition.Event -> copy(variables = distinctVariables)
            is FlowNodeDefinition.Unknown -> copy(variables = distinctVariables)
            is FlowNodeDefinition.Activity.Task -> copy(variables = distinctVariables, boundaryEventRefs = distinctBoundaryEventRefs())
            is FlowNodeDefinition.Activity.SubProcess -> copy(variables = distinctVariables, boundaryEventRefs = distinctBoundaryEventRefs())
            is FlowNodeDefinition.Activity.CallActivity -> copy(variables = distinctVariables, boundaryEventRefs = distinctBoundaryEventRefs())
        }
    }

    private fun FlowNodeDefinition.Activity.distinctBoundaryEventRefs(): List<String> = boundaryEventRefs.distinct().sorted()

    private fun FlowNodeDefinition.Activity.SubProcess.scope() = FlowScope(flowNodes, sequenceFlows)

    private fun FlowNodeDefinition.Activity.SubProcess.withScope(scope: FlowScope) = copy(flowNodes = scope.flowNodes, sequenceFlows = scope.sequenceFlows)
}
