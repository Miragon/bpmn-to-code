package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags call activities whose called element references a process that is not among the loaded
 * models — a dangling call activity that fails at deployment or throws at runtime. This can only
 * be judged with the whole related fileset loaded together, so the rule is opt-in (see BpmnRules).
 *
 * Only call activities that actually declare a called element are checked; a missing called element
 * is the concern of [MissingCalledElementRule].
 */
class CallActivityTargetExistsRule : CrossModelValidationRule {

    override val id = "call-activity-target-exists"
    override val severity = Severity.ERROR

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        return context.models.flatMap { model ->
            model.callActivities
                .filter { it.hasCalledElement() && context.resolveCalledModel(it) == null }
                .map { callActivity ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = callActivity.id,
                        processId = model.processId,
                        message = "Call activity '${callActivity.id}' references unknown process '${callActivity.getValue()}'.",
                    )
                }
        }
    }
}
