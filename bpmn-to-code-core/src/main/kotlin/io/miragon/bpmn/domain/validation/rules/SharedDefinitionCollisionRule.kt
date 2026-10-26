package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.Feel
import io.miragon.bpmn.domain.shared.VariableMapping
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CollisionDetail
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags job types, messages, signals, errors, escalations and process variables that collapse to the same generated
 * constant name. These are generated once for all processes, so a clash between two processes breaks the build
 * just like a clash within one.
 */
class SharedDefinitionCollisionRule : CrossModelValidationRule {

    override val id = "shared-definition-collision"
    override val severity = Severity.ERROR
    override val mandatory = true

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val models = context.models
        val collisions = listOf(
            findCollisionsIn(models, "ServiceTask") { it.serviceTasks },
            findCollisionsIn(models, "Message") { it.definitions.messages },
            findCollisionsIn(models, "Signal") { it.definitions.signals },
            findCollisionsIn(models, "Error") { it.definitions.errors },
            findCollisionsIn(models, "Escalation") { it.definitions.escalations },
            findCollisionsIn(models, "Variable") { it.variables },
        ).flatten()
        return collisions.map { violation(processId = it.processId, message = it.describe()) }
    }

    private fun <T : VariableMapping<*>> findCollisionsIn(
        models: List<ProcessModel>,
        variableType: String,
        itemsOf: (ProcessModel) -> List<T>,
    ): List<CollisionDetail> {
        val usages = models.flatMap { model ->
            itemsOf(model).filter { it.getName().isNotEmpty() && !Feel.isExpression(it.getRawName()) }.map { model.processId to it }
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
}
