package io.miragon.bpmn.domain.shared

/**
 * The flow nodes and sequence flows owned by one BPMN scope.
 *
 * BPMN calls this a `bpmn:FlowElementsContainer`: a process and a sub-process are containers in exactly
 * the same sense, each owning its children *and* the flows between them. Naming it once is what lets the
 * process model, its variants and [FlowNodeDefinition.Activity.SubProcess] share the concept instead of
 * each carrying the two lists apart — and what lets a reader hand back one value rather than a pair.
 *
 * This is the store. [ProcessGraph] is the flattened projection over it.
 */
data class FlowScope(
    val flowNodes: List<FlowNodeDefinition> = emptyList(),
    val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
) {

    /**
     * Merges this scope with the same scope of [others] by element id, unioning additive list fields like
     * `variables` and `boundaryEventRefs` so that variant-specific extension data (e.g. additionalInputVariables)
     * is preserved instead of being dropped by simple deduplication. Sub-process scopes are merged recursively,
     * so nesting survives the merge.
     *
     * A merged node's base attributes come from the first scope in the given order, this one first; a compensation
     * boundary event's handler comes from the first scope that declares one.
     */
    fun merge(others: List<FlowScope>): FlowScope {
        val scopes = listOf(this) + others
        val nodesById = scopes.flatMap { it.flowNodes }.filter { !it.id.isNullOrEmpty() }.groupBy { it.id }
        val mergedNodes = nodesById.map { (_, duplicates) -> mergeNodes(duplicates) }
        val mergedFlows = scopes.flatMap { it.sequenceFlows }.distinctBy { it.id.orEmpty() }
        return FlowScope(mergedNodes, mergedFlows)
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

    private fun mergeNodes(duplicates: List<FlowNodeDefinition>): FlowNodeDefinition {
        val merged = duplicates.first().mergedWith(duplicates.drop(1))
        if (merged !is FlowNodeDefinition.Activity.SubProcess) return merged
        val childScopes = duplicates.filterIsInstance<FlowNodeDefinition.Activity.SubProcess>().map { it.scope() }
        return merged.withScope(childScopes.first().merge(childScopes.drop(1)))
    }

    private fun FlowNodeDefinition.Activity.SubProcess.scope() = FlowScope(flowNodes, sequenceFlows)

    private fun FlowNodeDefinition.Activity.SubProcess.withScope(scope: FlowScope) = copy(flowNodes = scope.flowNodes, sequenceFlows = scope.sequenceFlows)
}
