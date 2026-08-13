package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags error events whose definition lacks a 'name' or 'errorCode' attribute.
 */
class MissingErrorDefinitionRule : SingleModelValidationRule {

    override val id = "missing-error-definition"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> = context.model.errorUsages()
        .filter { (_, error) -> error.errorRef != null && (error.errorName == null || error.errorCode == null) }
        .map { (node, _) ->
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = node.id,
                processId = context.model.processId,
                message = "Error event definition is missing a 'name' or 'errorCode' attribute.",
            )
        }
}
