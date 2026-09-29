package io.miragon.bpmn.domain.validation

import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnValidationExceptionTest {

    @Test
    fun `message sums up the violations and lists each of them`() {
        val exception = BpmnValidationException(
            listOf(
                ValidationViolation(ruleId = "rule-a", severity = Severity.ERROR, elementId = "Task_1", processId = "order", message = "Broken."),
                ValidationViolation(ruleId = "rule-b", severity = Severity.WARN, elementId = null, processId = "order", message = "Odd."),
            ),
        )

        assertThat(exception.message).isEqualTo(
            "BPMN validation failed: 1 error(s), 1 warning(s)\n" +
                "[BPMN VALIDATION ERROR] order/Task_1: Broken. (rule: rule-a)\n" +
                "[BPMN VALIDATION WARN] order: Odd. (rule: rule-b)",
        )
    }
}
