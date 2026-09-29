package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags call activities that reference no target process (missing 'calledElement' / 'processId').
 */
class MissingCalledElementRule : SingleModelValidationRule {

    override val id = "missing-called-element"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> = context.model.callActivities
        .filter { !it.hasCalledElement() }.map { callActivity ->
            violation(
                processId = context.model.processId,
                elementId = callActivity.id,
                message = "Call activity is missing a 'calledElement' or 'processId' attribute.",
            )
        }
}
