package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Warns when a process contains no flow nodes, so no meaningful API can be generated from it.
 */
class EmptyProcessRule : SingleModelValidationRule {

    override val id = "empty-process"
    override val severity = Severity.WARN

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        if (context.model.flowNodes.isEmpty()) {
            return listOf(
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = null,
                    processId = context.model.processId,
                    message = "Process has no elements defined.",
                )
            )
        }
        return emptyList()
    }
}
