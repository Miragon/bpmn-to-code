package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Opt-in rule: a timer value must be valid ISO-8601 for its type — `Date` -> date/time,
 * `Duration` -> duration, `Cycle` -> repeating interval.
 * Dynamic expressions and blank values are skipped (the latter is the domain of
 * [MissingTimerDefinitionRule]); timers with an unknown/absent type are ignored.
 */
class TimerIso8601SyntaxRule : SingleModelValidationRule {

    override val id = "timer-iso8601-syntax"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.timers.mapNotNull { timer ->
            val type = timer.type ?: return@mapNotNull null
            val value = timer.expression.orEmpty()
            if (value.isBlank() || TimerValueSyntax.isExpression(value)) return@mapNotNull null
            val valid = when (type) {
                TimerType.DATE -> TimerValueSyntax.isValidIsoDateTime(value)
                TimerType.DURATION -> TimerValueSyntax.isValidIsoDuration(value)
                TimerType.CYCLE -> TimerValueSyntax.isValidIsoRepeatingInterval(value)
            }
            if (valid) return@mapNotNull null
            violation(processId = context.model.processId, elementId = timer.id, message = "Timer ${type.label} value '$value' is not valid ISO-8601.")
        }
    }
}
