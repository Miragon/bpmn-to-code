package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Opt-in rule: a `timeCycle` timer must contain a valid cron expression.
 * Only `Cycle` timers are checked — cron has no meaning for `Date` / `Duration` timers, which are
 * always ISO-8601. Dynamic expressions and blank values are skipped (the latter is the domain of
 * [MissingTimerDefinitionRule]). The check is syntactic (field count + allowed characters), not a
 * full engine-specific cron evaluation.
 */
class TimerCronSyntaxRule : SingleModelValidationRule {

    override val id = "timer-cron-syntax"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.timers.mapNotNull { timer ->
            if (timer.type != TimerType.CYCLE) return@mapNotNull null
            val value = timer.expression.orEmpty()
            if (value.isBlank() || TimerValueSyntax.isExpression(value)) return@mapNotNull null
            if (TimerValueSyntax.isValidCron(value)) return@mapNotNull null
            violation(processId = context.model.processId, elementId = timer.id, message = "Timer cycle '$value' is not a valid cron expression.")
        }
    }
}
