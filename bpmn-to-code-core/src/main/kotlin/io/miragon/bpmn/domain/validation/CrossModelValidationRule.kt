package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * A rule that reasons across all loaded process models at once — for example to resolve
 * a call activity's called element to the model of the called process. Invoked a single
 * time with the whole model set, unlike [SingleModelValidationRule] which is invoked per model.
 */
interface CrossModelValidationRule : ValidationRule {
    fun validate(context: CrossModelValidationContext): List<ValidationViolation>
}
