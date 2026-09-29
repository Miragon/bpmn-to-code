package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidationRuleTest {

    private val underTest = SampleRule()

    @Test
    fun `builds a violation carrying the rule's id and severity`() {
        val violation = underTest.violation(processId = "order", message = "Broken.", elementId = "Task_1")

        assertThat(violation).isEqualTo(
            ValidationViolation(ruleId = "sample-rule", severity = Severity.ERROR, elementId = "Task_1", processId = "order", message = "Broken."),
        )
    }

    @Test
    fun `builds a violation of the whole process when no element is given`() {
        assertThat(underTest.violation(processId = "order", message = "Broken.").elementId).isNull()
    }

    @Test
    fun `lets a single finding deviate from the rule's severity`() {
        val violation = underTest.violation(processId = "order", message = "Odd.", severity = Severity.WARN)

        assertThat(violation.severity).isEqualTo(Severity.WARN)
    }

    private class SampleRule : ValidationRule {
        override val id = "sample-rule"
        override val severity = Severity.ERROR
    }
}
