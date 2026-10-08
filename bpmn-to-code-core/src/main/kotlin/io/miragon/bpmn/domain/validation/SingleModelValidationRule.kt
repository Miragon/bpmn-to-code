package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

interface SingleModelValidationRule : ValidationRule {
    fun validate(context: SingleModelValidationContext): List<ValidationViolation>
}
