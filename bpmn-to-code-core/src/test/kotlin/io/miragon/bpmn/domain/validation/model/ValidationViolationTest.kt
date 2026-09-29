package io.miragon.bpmn.domain.validation.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidationViolationTest {

    @Test
    fun `describes a violation of an element by process and element id`() {
        val violation = ValidationViolation(ruleId = "rule-a", severity = Severity.ERROR, elementId = "Task_1", processId = "order", message = "Broken.")

        assertThat(violation.describe()).isEqualTo("[BPMN VALIDATION ERROR] order/Task_1: Broken. (rule: rule-a)")
    }

    @Test
    fun `describes a violation of the whole process by its process id`() {
        val violation = ValidationViolation(ruleId = "rule-b", severity = Severity.WARN, elementId = null, processId = "order", message = "Odd.")

        assertThat(violation.describe()).isEqualTo("[BPMN VALIDATION WARN] order: Odd. (rule: rule-b)")
    }
}
