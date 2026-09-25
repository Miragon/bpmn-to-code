package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.service.CollisionDetectionService
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags job types, messages, signals, errors and escalations that collapse to the same generated constant
 * name. These are generated once for all processes, so a clash between two processes breaks the build
 * just like a clash within one.
 */
class SharedDefinitionCollisionRule(
    private val collisionDetectionService: CollisionDetectionService = CollisionDetectionService(),
) : CrossModelValidationRule {

    override val id = "shared-definition-collision"
    override val severity = Severity.ERROR
    override val mandatory = true

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val collisions = collisionDetectionService.findSharedCollisions(context.models)
        return collisions.map { detail ->
            val conflicting = detail.conflictingIds.joinToString(", ")
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = null,
                processId = detail.processId,
                message = "[${detail.variableType}] '${detail.constantName}' has conflicting IDs: $conflicting",
            )
        }
    }
}
