package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.ProcessModel.Variant
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowScope

class ModelMergerService {

    /**
     * Merges process models by process id.
     * A process backed by a single BPMN file keeps an empty [ProcessModel.variants].
     * A process backed by several files gains one [Variant] per file, with [ProcessModel.flowNodes]
     * holding their union.
     */
    fun mergeModels(models: List<ProcessModel>): List<ProcessModel> {
        val groupedModels = models.groupBy { it.processId }.entries.sortedBy { it.key }
        return groupedModels.map { (processId, modelsOfProcess) -> merge(processId, modelsOfProcess).sortContent() }
    }

    private fun merge(processId: String, models: List<ProcessModel>): ProcessModel {
        if (models.size == 1) return models.first().deduplicated()
        requireVariantNames(processId, models)
        val sorted = models.sortedBy { requireNotNull(it.variantName) }
        val merged = mergeScopes(sorted.map { it.scope() })
        return ProcessModel(
            processId = processId,
            processName = sorted.firstNotNullOfOrNull { it.processName },
            flowNodes = merged.flowNodes,
            sequenceFlows = merged.sequenceFlows,
            definitions = sorted.first().definitions.merge(sorted.drop(1).map { it.definitions }),
            isExecutable = sorted.any { it.isExecutable },
            detectedEngine = sorted.firstNotNullOfOrNull { it.detectedEngine },
            variants = sorted.map { Variant(requireNotNull(it.variantName), it.flowNodes, it.sequenceFlows) },
        )
    }

    /**
     * A single file still goes through the merge, so duplicate ids inside one model collapse the same way.
     */
    private fun ProcessModel.deduplicated(): ProcessModel {
        val merged = mergeScopes(listOf(scope()))
        return copy(
            flowNodes = merged.flowNodes,
            sequenceFlows = merged.sequenceFlows,
            definitions = definitions.merge(emptyList()),
        )
    }

    private fun requireVariantNames(processId: String, models: List<ProcessModel>) {
        require(models.none { it.variantName.isNullOrBlank() }) {
            "Multiple BPMN files share process ID '$processId' but not all define a variantName. " +
                    "Add a variantName extension property to each process."
        }
    }

    /**
     * Merges the same scope across models by element id, unioning additive list fields like `variables`
     * and `boundaryEventRefs` so that variant-specific extension data (e.g. additionalInputVariables) is
     * preserved instead of being dropped by simple deduplication. Sub-process scopes are merged
     * recursively, so nesting survives the merge.
     *
     * A merged node's base attributes come from the first model in the given order. Callers pass models
     * pre-sorted by `variantName`, so the result is a deterministic function of the inputs rather than of
     * filesystem read order.
     */
    private fun mergeScopes(scopes: List<FlowScope>): FlowScope {
        val nodesById = scopes
            .flatMap { it.flowNodes }
            .filter { it.getRawName().isNotEmpty() }
            .groupBy { it.getRawName() }
        val mergedNodes = nodesById.map { (_, duplicates) -> mergeNodes(duplicates) }
        val mergedFlows = scopes.flatMap { it.sequenceFlows }.distinctBy { it.getRawName() }
        return FlowScope(mergedNodes, mergedFlows)
    }

    private fun mergeNodes(duplicates: List<FlowNodeDefinition>): FlowNodeDefinition {
        val base = duplicates.first()
        val merged = base.mergedWith(duplicates.drop(1))
        if (merged !is FlowNodeDefinition.Activity.SubProcess) return merged
        val childScopes = duplicates
            .filterIsInstance<FlowNodeDefinition.Activity.SubProcess>()
            .map { it.scope() }
        val mergedChildren = mergeScopes(childScopes)
        return merged.copy(flowNodes = mergedChildren.flowNodes, sequenceFlows = mergedChildren.sequenceFlows)
    }

    private fun ProcessModel.sortContent(): ProcessModel {
        val sorted = scope().sorted()
        return copy(
            flowNodes = sorted.flowNodes,
            sequenceFlows = sorted.sequenceFlows,
            definitions = definitions.sorted(),
            variants = variants.map { variant ->
                val sortedVariant = variant.scope().sorted()
                variant.copy(flowNodes = sortedVariant.flowNodes, sequenceFlows = sortedVariant.sequenceFlows)
            },
        )
    }

    /**
     * Sorts a scope and every scope nested inside it, so generated output is a function of the model
     * rather than of the order the files happened to be read in.
     */
    private fun FlowScope.sorted(): FlowScope {
        val sortedNodes = flowNodes
            .map { node -> if (node is FlowNodeDefinition.Activity.SubProcess) node.sortedRecursively() else node }
            .sortedBy { it.getRawName() }
        return FlowScope(sortedNodes, sequenceFlows.sortedBy { it.getRawName() })
    }

    private fun FlowNodeDefinition.Activity.SubProcess.sortedRecursively(): FlowNodeDefinition {
        val sorted = scope().sorted()
        return copy(flowNodes = sorted.flowNodes, sequenceFlows = sorted.sequenceFlows)
    }

    /**
     * Merging and sorting treat a scope as one value, so it is read out here. The models themselves name
     * their two halves rather than storing the pair.
     */
    private fun ProcessModel.scope() = FlowScope(flowNodes, sequenceFlows)

    private fun Variant.scope() = FlowScope(flowNodes, sequenceFlows)

    private fun FlowNodeDefinition.Activity.SubProcess.scope() = FlowScope(flowNodes, sequenceFlows)
}
