package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Common supertype for all validation rules, allowing single-model and cross-model
 * rules to be configured through the same fluent flow. Rules should implement exactly one
 * of [SingleModelValidationRule] or [CrossModelValidationRule] — a rule that implements only
 * this marker carries no `validate` method and is never executed.
 */
interface ValidationRule {
    val id: String
    val severity: Severity
    val mandatory: Boolean get() = false

    /**
     * A finding of this rule. It carries the rule's [id] and, unless the finding needs another one, its [severity].
     */
    fun violation(
        processId: String,
        message: String,
        elementId: String? = null,
        severity: Severity = this.severity,
    ): ValidationViolation = ValidationViolation(
        ruleId = id,
        severity = severity,
        elementId = elementId,
        processId = processId,
        message = message,
    )
}
