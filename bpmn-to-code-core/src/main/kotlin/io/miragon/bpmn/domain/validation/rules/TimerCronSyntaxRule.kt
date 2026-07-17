package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.BpmnValidationRule
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
class TimerCronSyntaxRule : BpmnValidationRule {

    override val id = "timer-cron-syntax"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.timers.mapNotNull { timer ->
            val (type, value) = timer.getValue()
            if (type != "Cycle") return@mapNotNull null
            if (value.isBlank() || TimerValueSyntax.isExpression(value)) return@mapNotNull null
            if (TimerValueSyntax.isValidCron(value)) return@mapNotNull null
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = timer.id,
                processId = context.model.processId,
                message = "Timer cycle '$value' is not a valid cron expression.",
            )
        }
    }
}
