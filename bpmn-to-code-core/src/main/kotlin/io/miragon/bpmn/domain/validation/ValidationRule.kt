package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.Severity

/**
 * Common supertype for all validation rules, allowing single-model and cross-model
 * rules to be configured through the same fluent flow. Rules should implement exactly one
 * of [SingleModelValidationRule] or [CrossModelValidationRule] — a rule that implements only
 * this marker carries no `validate` method and is never executed.
 */
interface ValidationRule {
    val id: String
    val severity: Severity

    /**
     * Marks a rule as integrity-critical for code generation — it guarantees the generated code can
     * emit a valid, unique constant name. Such rules cannot be turned off via `disabledRules` on the
     * generation/validation path. The testing module generates no code and ignores this flag, so
     * every rule stays disableable there.
     */
    val mandatory: Boolean get() = false
}
