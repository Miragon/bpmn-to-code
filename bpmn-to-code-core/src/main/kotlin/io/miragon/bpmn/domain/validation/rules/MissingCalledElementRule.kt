package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class MissingCalledElementRule : SingleModelValidationRule {

    override val id = "missing-called-element"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.callActivities
            .filter { !it.hasCalledElement() }
            .map { callActivity ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = callActivity.id,
                    processId = context.model.processId,
                    message = "Call activity is missing a 'calledElement' or 'processId' attribute.",
                )
            }
    }
}
