package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessApiNames
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.CollisionDetail
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags when distinct BPMN elements collapse to the same generated name within one Process API, so only one of them
 * would be emitted. Flow nodes are named model-wide (the flat `FlowNodes`), call-activity mappings per node.
 * Identifiers generated once for all processes are checked by [SharedDefinitionCollisionRule].
 */
class CollisionDetectionRule : SingleModelValidationRule {

    override val id = "collision-detection"
    override val severity = Severity.ERROR
    override val mandatory = true

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        val flowNodes = model.allFlowNodes
        val flowNodeCollisions = findCollisionsIn(
            processId = model.processId,
            items = flowNodes,
            variableType = "FlowNode",
            rawName = { it.id.orEmpty() },
            constantName = { ProcessApiNames.elementName(it.id.orEmpty()) },
        )
        val repeatedIds = findRepeatedIds(model.processId, flowNodes)
        val mappingCollisions = flowNodes.flatMap { findCollisionsOn(model.processId, it) }
        val collisions = (flowNodeCollisions + repeatedIds + mappingCollisions)
            .distinctBy { Triple(first = it.processId, second = it.variableType, third = it.conflictingIds) }
        return collisions.map { violation(processId = it.processId, message = it.describe()) }
    }

    private fun findCollisionsOn(processId: String, node: FlowNodeDefinition): List<CollisionDetail> {
        val callActivity = (node as? FlowNodeDefinition.Activity.CallActivity)?.definition ?: return emptyList()
        return findMappingCollisions(processId, callActivity.inputMappings) + findMappingCollisions(processId, callActivity.outputMappings)
    }

    private fun findMappingCollisions(processId: String, mappings: List<CallActivityDefinition.Mapping>): List<CollisionDetail> = findCollisionsIn(
        processId = processId,
        items = mappings.filter { !it.target.isNullOrBlank() },
        variableType = "CallActivityMapping",
        rawName = { it.target!! },
        constantName = { it.target!!.toUpperSnakeCase() },
    )

    /**
     * The same element id declared in two scopes (e.g. at the root and inside a subprocess) stays two nodes,
     * which the flat `FlowNodes` object would emit twice under one name.
     */
    private fun findRepeatedIds(processId: String, flowNodes: List<FlowNodeDefinition>): List<CollisionDetail> = flowNodes
        .map { it.id.orEmpty() }
        .filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.map { (id, occurrences) ->
            CollisionDetail(
                processId = processId,
                variableType = "FlowNode",
                constantName = ProcessApiNames.elementName(id),
                conflictingIds = List(occurrences) { id },
            )
        }

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
