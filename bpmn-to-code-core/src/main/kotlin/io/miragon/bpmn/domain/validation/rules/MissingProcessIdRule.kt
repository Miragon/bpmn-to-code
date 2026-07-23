package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags a model with a blank process ID, which is required to identify the generated API.
 */
class MissingProcessIdRule : SingleModelValidationRule {

    override val id = "missing-process-id"
    override val severity = Severity.ERROR
    override val mandatory = true

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        if (context.model.processId.isBlank()) {
            return listOf(
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = null,
                    processId = "(unknown)",
                    message = "BPMN model is missing a process ID.",
                )
            )
        }
        return emptyList()
    }
}
