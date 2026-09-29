package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.ValidationViolation

class BpmnValidationException(val violations: List<ValidationViolation>) : RuntimeException(buildMessage(violations)) {

    companion object {
        private fun buildMessage(violations: List<ValidationViolation>): String {
            val details = violations.joinToString("\n") { it.describe() }
            return "${ValidationResult(violations).failureSummary}\n$details"
        }
    }
}
