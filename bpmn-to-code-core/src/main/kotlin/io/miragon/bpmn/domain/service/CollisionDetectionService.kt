package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.VariableMapping
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase
import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase
import io.miragon.bpmn.domain.validation.model.CollisionDetail

/**
 * Domain service responsible for detecting name collisions in BPMN models.
 * Such a collision occurs when different ids (for example, of two service tasks)
 * would normalize to the same identifier in the generated Process API.
 *
 * If this is the case, it cannot be guaranteed that the Process API is complete,
 * because the two elements could differ and only the first would be emitted.
 *
 * Each check mirrors one scope of the generated API: flow nodes are named model-wide (the flat `Flow`),
 * variables, sequence flows and call-activity mappings per node, and the shared definitions across all
 * models of a run ([findSharedCollisions]).
 */
class CollisionDetectionService {

    fun findCollisions(model: ProcessModel): List<CollisionDetail> {
        val modelId = model.processId
        val collisions = mutableListOf<CollisionDetail>()
        collisions.addAll(findCollisionsIn(modelId, model.allFlowNodes, "FlowNode") { it.getRawName().toCamelCase() })
        collisions.addAll(findRepeatedIds(modelId, model.allFlowNodes))
        model.allFlowNodes.forEach { node -> collisions.addAll(findCollisionsOn(modelId, node, model)) }
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

    private fun findCollisionsOn(processId: String, node: FlowNodeDefinition, model: ProcessModel): List<CollisionDetail> {
        val collisions = mutableListOf<CollisionDetail>()
        collisions.addAll(findCollisionsIn(processId, node.variables, "Variable"))
        collisions.addAll(findCollisionsIn(processId, model.graph.outgoingFlowsOf(node), "SequenceFlow") { it.getRawName().toCamelCase() })
        (node as? FlowNodeDefinition.Activity.CallActivity)?.definition?.let { callActivity ->
            collisions.addAll(findMappingCollisions(processId, callActivity.inputMappings))
            collisions.addAll(findMappingCollisions(processId, callActivity.outputMappings))
        }
        return collisions
    }

    private fun findMappingCollisions(processId: String, mappings: List<CallActivityDefinition.Mapping>): List<CollisionDetail> = findCollisionsIn(
        processId = processId,
        items = mappings.filter { !it.target.isNullOrBlank() },
        variableType = "CallActivityMapping",
        rawName = { it.target!! },
        constantName = { it.target!!.toUpperSnakeCase() },
    )

    /**
     * The same element id declared in two scopes (e.g. at the root and inside a subprocess) survives merging as
     * two nodes, which the flat `Flow` object would emit twice under one name.
     */
    private fun findRepeatedIds(processId: String, flowNodes: List<VariableMapping<*>>): List<CollisionDetail> = flowNodes
        .map { it.getRawName() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .map { (id, occurrences) ->
            CollisionDetail(
                processId = processId,
                variableType = "FlowNode",
                constantName = id.toCamelCase(),
                conflictingIds = List(occurrences) { id },
            )
        }

    private fun <T : VariableMapping<*>> findCollisionsIn(
        processId: String,
        items: List<T>,
        variableType: String,
        constantName: (T) -> String = { it.getName() },
    ): List<CollisionDetail> = findCollisionsIn(processId, items, variableType, { it.getRawName() }, constantName)

    private fun <T> findCollisionsIn(
        processId: String,
        items: List<T>,
        variableType: String,
        rawName: (T) -> String,
        constantName: (T) -> String,
    ): List<CollisionDetail> {
        val distinctItems = items.filter { rawName(it).isNotEmpty() }.distinctBy { rawName(it) }
        val relevantItems = distinctItems.filter { constantName(it).isNotEmpty() }
        val itemsPerConstantName = relevantItems.groupBy(constantName)
        val collisions = itemsPerConstantName.filterValues { it.size > 1 }
        return collisions.map { (name, itemsWithSameName) ->
            CollisionDetail(
                processId = processId,
                variableType = variableType,
                constantName = name,
                conflictingIds = itemsWithSameName.map(rawName).sorted(),
            )
        }
    }
}
