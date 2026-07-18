package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.service.CollisionDetectionService
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags when distinct BPMN elements collapse to the same generated constant name, causing ID clashes.
 * Runs post-merge, so it also catches collisions introduced by combining models.
 */
class CollisionDetectionRule(
    private val collisionDetectionService: CollisionDetectionService = CollisionDetectionService(),
) : SingleModelValidationRule {

    override val id = "collision-detection"
    override val severity = Severity.ERROR
    override val phase = ValidationPhase.POST_MERGE

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val collisions = collisionDetectionService.findCollisions(context.model)
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
