package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BpmnValidationAssertTest {

    private val error = ValidationViolation(
        ruleId = "test-rule",
        severity = Severity.ERROR,
        elementId = "Task_1",
        processId = "myProcess",
        message = "Something is wrong",
    )

    private val warning = ValidationViolation(
        ruleId = "warn-rule",
        severity = Severity.WARN,
        elementId = null,
        processId = "myProcess",
        message = "Something might be wrong",
    )

    @Nested
    inner class AssertNoViolations {

        @Test
        fun `passes when no violations`() {
            val result = ValidationResult(emptyList())
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertNoViolations()
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when violations exist`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertNoViolations()
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected no violations but found 1")
                .hasMessageContaining("[ERROR] myProcess/Task_1: Something is wrong (rule: test-rule)")
        }
    }

    @Nested
    inner class AssertNoViolationsForRule {

        @Test
        fun `passes when no matching violations`() {
            val result = ValidationResult(listOf(error))
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertNoViolations("other-rule")
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when matching violations exist`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertNoViolations("test-rule")
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected no violations for rule 'test-rule'")
        }
    }

    @Nested
    inner class AssertHasViolations {

        @Test
        fun `passes when violations exist`() {
            val result = ValidationResult(listOf(error))
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertHasViolations()
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when no violations`() {
            val result = ValidationResult(emptyList())
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertHasViolations()
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected at least one violation but found none")
        }
    }

    @Nested
    inner class AssertViolation {

        @Test
        fun `passes when exactly one violation matches the rule`() {
            val result = ValidationResult(listOf(error))
            assertThatCode {
                BpmnValidationAssert.assertThat(result)
                    .assertViolation("test-rule", elementId = "Task_1", messageContains = "wrong")
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when no violation matches the rule`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertViolation("other-rule")
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected exactly one violation for rule 'other-rule' but found 0")
        }

        @Test
        fun `fails when the element does not match`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertViolation("test-rule", elementId = "Task_2")
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("on element 'Task_2'")
        }

        @Test
        fun `fails when the message does not contain the fragment`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertViolation("test-rule", messageContains = "missing")
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("to contain 'missing'")
        }
    }

    @Nested
    inner class AssertViolationCount {

        @Test
        fun `passes when the count matches`() {
            val result = ValidationResult(listOf(error, warning))
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertViolationCount(2)
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when the count differs`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertViolationCount(2)
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected 2 violation(s) but found 1")
        }
    }

    @Nested
    inner class AssertNoErrors {

        @Test
        fun `passes when only warnings`() {
            val result = ValidationResult(listOf(warning))
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertNoErrors()
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when errors exist`() {
            val result = ValidationResult(listOf(error))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertNoErrors()
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected no errors but found 1")
        }
    }

    @Nested
    inner class AssertNoWarnings {

        @Test
        fun `passes when only errors`() {
            val result = ValidationResult(listOf(error))
            assertThatCode {
                BpmnValidationAssert.assertThat(result).assertNoWarnings()
            }.doesNotThrowAnyException()
        }

        @Test
        fun `fails when warnings exist`() {
            val result = ValidationResult(listOf(warning))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertNoWarnings()
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Expected no warnings but found 1")
        }
    }

    @Nested
    inner class Result {

        @Test
        fun `returns the underlying ValidationResult`() {
            val result = ValidationResult(listOf(error))
            val assert = BpmnValidationAssert.assertThat(result)
            assertThat(assert.result()).isSameAs(result)
        }
    }

    @Nested
    inner class FormatViolations {

        @Test
        fun `shows processId only when elementId is null`() {
            val result = ValidationResult(listOf(warning))
            assertThatThrownBy {
                BpmnValidationAssert.assertThat(result).assertNoViolations()
            }.isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("[WARN] myProcess: Something might be wrong (rule: warn-rule)")
        }
    }
}
