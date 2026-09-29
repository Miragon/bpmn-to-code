package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

data class ValidationResult(val violations: List<ValidationViolation>) {
    val errors: List<ValidationViolation> get() = violations.filter { it.severity == Severity.ERROR }
    val warnings: List<ValidationViolation> get() = violations.filter { it.severity == Severity.WARN }
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val isValid: Boolean get() = violations.isEmpty()
    val failureSummary: String get() = "BPMN validation failed: ${errors.size} error(s), ${warnings.size} warning(s)"
    fun failures(failOnWarning: Boolean): List<ValidationViolation> = if (failOnWarning) errors + warnings else errors
    fun hasFailures(failOnWarning: Boolean): Boolean = failures(failOnWarning).isNotEmpty()
}
