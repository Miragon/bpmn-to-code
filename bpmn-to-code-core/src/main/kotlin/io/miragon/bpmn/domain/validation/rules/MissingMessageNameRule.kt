package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags message elements that are missing a 'name' attribute.
 */
class MissingMessageNameRule : SingleModelValidationRule {

    override val id = "missing-message-name"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.messages
            .filter { !it.hasName() }
            .map { message ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = message.id,
                    processId = context.model.processId,
                    message = "Message element is missing a 'name' attribute.",
                )
            }
    }
}
