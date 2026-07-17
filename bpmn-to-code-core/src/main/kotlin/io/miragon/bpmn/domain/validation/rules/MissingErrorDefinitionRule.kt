package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class MissingErrorDefinitionRule : SingleModelValidationRule {

    override val id = "missing-error-definition"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.errors
            .filter { !it.hasRequiredFields() }
            .map { error ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = error.id,
                    processId = context.model.processId,
                    message = "Error event definition is missing a 'name' or 'errorCode' attribute.",
                )
            }
    }
}
