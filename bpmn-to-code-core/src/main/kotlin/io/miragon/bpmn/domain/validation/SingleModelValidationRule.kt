package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation

interface SingleModelValidationRule : ValidationRule {
    val phase: ValidationPhase get() = ValidationPhase.PRE_MERGE
    fun validate(context: SingleModelValidationContext): List<ValidationViolation>
}

/*
 * Backward-compatibility alias for the previous type name. Kept as a temporary migration aid;
 * scheduled for removal in v5.0.0. Migrate to [SingleModelValidationRule].
 */
@Deprecated(
    message = "Renamed to SingleModelValidationRule; this alias will be removed in v5.0.0",
    replaceWith = ReplaceWith("SingleModelValidationRule"),
)
typealias BpmnValidationRule = SingleModelValidationRule
