package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class MissingTimerDefinitionRule : SingleModelValidationRule {

    override val id = "missing-timer-definition"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.timers
            .filter { !it.hasTimerType() }
            .map { timer ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = timer.id,
                    processId = context.model.processId,
                    message = "Timer event definition has no valid type (Date, Duration, or Cycle).",
                )
            }
    }
}
