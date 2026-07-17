package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation

interface SingleModelValidationRule : ValidationRule {
    val phase: ValidationPhase get() = ValidationPhase.PRE_MERGE
    fun validate(context: SingleModelValidationContext): List<ValidationViolation>
}
