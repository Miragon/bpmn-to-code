package io.miragon.bpmn.domain.validation.model

data class ValidationViolation(
    val ruleId: String,
    val severity: Severity,
    val elementId: String?,
    val processId: String,
    val message: String,
) {

    /**
     * The one-line form the build plugins log and failure messages list.
     */
    fun describe(): String {
        val location = if (elementId != null) "$processId/$elementId" else processId
        return "[BPMN VALIDATION ${severity.name}] $location: $message (rule: $ruleId)"
    }
}
