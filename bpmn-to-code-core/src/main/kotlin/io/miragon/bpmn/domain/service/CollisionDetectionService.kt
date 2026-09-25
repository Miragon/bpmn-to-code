package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.VariableMapping
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase
import io.miragon.bpmn.domain.validation.model.CollisionDetail

/**
 * Domain service responsible for detecting name collisions in BPMN models.
 * Such a collision occurs when different configs (for example, for serviceTasks)
 * would normalize to the same constant name in the processApi
 *
 * If this is the case, it cannot be guaranteed that the Process API is complete,
 * because the variables could have different values and only the first would be included.
 *
 * Thus, this class is responsible for detecting such collisions.
 */
class CollisionDetectionService {

    fun findCollisions(model: ProcessModel): List<CollisionDetail> {
        val modelId = model.processId
        val collisions = mutableListOf<CollisionDetail>()
        collisions.addAll(findCollisionsIn(modelId, model.allFlowNodes, "FlowNode"))
        collisions.addAll(findCollisionsIn(modelId, model.timers, "Timer"))
        collisions.addAll(findCollisionsIn(modelId, model.variables, "Variable"))
        collisions.addAll(findCollisionsIn(modelId, model.allFlowNodes, "FlowNode") { it.getRawName().toCamelCase() })
        return collisions.distinctBy { Triple(it.processId, it.variableType, it.conflictingIds) }
    }

    fun findSharedCollisions(models: List<ProcessModel>): List<CollisionDetail> = listOf(
        findSharedCollisionsIn(models, "ServiceTask") { it.serviceTasks },
        findSharedCollisionsIn(models, "Message") { it.definitions.messages },
        findSharedCollisionsIn(models, "Signal") { it.definitions.signals },
        findSharedCollisionsIn(models, "Error") { it.definitions.errors },
        findSharedCollisionsIn(models, "Escalation") { it.definitions.escalations },
    ).flatten()

    private fun <T : VariableMapping<*>> findSharedCollisionsIn(
        models: List<ProcessModel>,
        variableType: String,
        itemsOf: (ProcessModel) -> List<T>,
    ): List<CollisionDetail> {
        val usages = models.flatMap { model ->
            itemsOf(model).filter { it.getName().isNotEmpty() }.map { model.processId to it }
        }
        val usagesPerConstantName = usages.groupBy { (_, item) -> item.getName() }
        return usagesPerConstantName.mapNotNull { (constantName, usagesWithSameName) ->
            val distinctItems = usagesWithSameName.map { (_, item) -> item }.distinctBy { it.getValue() }
            if (distinctItems.size < 2) return@mapNotNull null
            CollisionDetail(
                processId = usagesWithSameName.map { (processId, _) -> processId }.distinct().sorted().joinToString(", "),
                variableType = variableType,
                constantName = constantName,
                conflictingIds = distinctItems.map { it.getRawName() }.sorted(),
            )
        }
    }

    private fun <T : VariableMapping<*>> findCollisionsIn(
        processId: String,
        items: List<T>,
        variableType: String,
        nameSelector: (T) -> String = { it.getName() },
    ): List<CollisionDetail> {
        val distinctItems = items.filter { it.getRawName().isNotEmpty() }.distinctBy { it.getRawName() }
        val relevantItems = distinctItems.filter { nameSelector(it).isNotEmpty() }
        val itemsPerVariableName = relevantItems.groupBy(nameSelector)
        val collisions = itemsPerVariableName.filterValues { it.size > 1 }
        return collisions.mapNotNull { (constantName, itemsWithSameName) ->
            val rawNames = itemsWithSameName.map { it.getRawName() }
            if (rawNames.isEmpty()) return@mapNotNull null
            CollisionDetail(
                processId = processId,
                variableType = variableType,
                constantName = constantName,
                conflictingIds = rawNames.sorted(),
            )
        }
    }
}
