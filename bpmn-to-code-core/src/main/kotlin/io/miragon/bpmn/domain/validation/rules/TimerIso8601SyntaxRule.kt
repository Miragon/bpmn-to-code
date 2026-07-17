package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.BpmnValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Opt-in rule: a timer value must be valid ISO-8601 for its type — `Date` -> date/time,
 * `Duration` -> duration, `Cycle` -> repeating interval.
 * Dynamic expressions and blank values are skipped (the latter is the domain of
 * [MissingTimerDefinitionRule]); timers with an unknown/absent type are ignored.
 */
class TimerIso8601SyntaxRule : BpmnValidationRule {

    override val id = "timer-iso8601-syntax"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.timers.mapNotNull { timer ->
            val (type, value) = timer.getValue()
            if (value.isBlank() || TimerValueSyntax.isExpression(value)) return@mapNotNull null
            val valid = when (type) {
                "Date" -> TimerValueSyntax.isValidIsoDateTime(value)
                "Duration" -> TimerValueSyntax.isValidIsoDuration(value)
                "Cycle" -> TimerValueSyntax.isValidIsoRepeatingInterval(value)
                else -> return@mapNotNull null
            }
            if (valid) return@mapNotNull null
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = timer.id,
                processId = context.model.processId,
                message = "Timer $type value '$value' is not valid ISO-8601.",
            )
        }
    }
}
