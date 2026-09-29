package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.NamedEventUsage
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags every usage in the [flagged] direction whose name no usage in the other direction shares, anywhere in the
 * loaded models. Reported as WARN, since the counterpart may live outside the loaded fileset.
 */
abstract class UnmatchedEventUsageRule(private val flagged: EventDirection) : CrossModelValidationRule {

    override val severity = Severity.WARN

    protected abstract fun usagesOf(model: ProcessModel): List<NamedEventUsage>

    protected abstract fun message(usage: NamedEventUsage): String

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val matchedNames = context.models.flatMap { usagesOf(it) }.filter { it.direction != flagged }.map { it.name }.toSet()
        return context.models.flatMap { model ->
            usagesOf(model)
                .filter { it.direction == flagged && it.name !in matchedNames }
                .map { violation(processId = model.processId, elementId = it.node.id, message = message(it)) }
        }
    }
}
