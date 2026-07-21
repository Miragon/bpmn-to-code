package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * End-to-end test that exercises the testing module the way a real user would.
 * Acts as living documentation of the intended user experience.
 */
class BpmnProcessArchitectureTest {

    /**
     * Custom rule: all service tasks must have an ID that starts with a known prefix.
     */
    private class ServiceTaskNamingRule : SingleModelValidationRule {
        override val id = "service-task-naming"
        override val severity = Severity.WARN
        override val phase = ValidationPhase.PRE_MERGE

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            return context.model.serviceTasks
                .filter { task ->
                    val name = task.id ?: ""
                    !name.startsWith("Activity_") && !name.startsWith("Task_")
                }
                .map { task ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = task.id,
                        processId = context.model.processId,
                        message = "Service task '${task.id}' should start with 'Activity_' or 'Task_'",
                    )
                }
        }
    }

    @Test
    fun `validate shared bpmn files with Camunda 7 using built-in and custom rules`() {
        val assert = BpmnValidator
            .fromClasspath("bpmn/c7-subscribe-newsletter.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(
                BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION,
                BpmnRules.MISSING_MESSAGE_NAME,
                BpmnRules.MISSING_ELEMENT_ID,
                ServiceTaskNamingRule(),
            )
            .validate()

        assert.assertNoErrors()
        assert.assertNoViolations("missing-service-task-implementation")
        assert.assertNoViolations("missing-message-name")
        assert.assertNoViolations("missing-element-id")
    }

    @Test
    fun `validate shared bpmn files with Zeebe`() {
        BpmnValidator
            .fromClasspath("bpmn/c8-subscribe-newsletter.bpmn")
            .engine(ProcessEngine.ZEEBE)
            .withRules(
                BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION,
                BpmnRules.MISSING_MESSAGE_NAME,
                BpmnRules.MISSING_ELEMENT_ID,
            )
            .validate()
            .assertNoErrors()
    }

    @Test
    fun `disableRules filters violations and assertNoViolations confirms`() {
        BpmnValidator
            .fromClasspath("bpmn/invalid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
            .disableRules("missing-service-task-implementation")
            .validate()
            .assertNoViolations("missing-service-task-implementation")
    }

    @Test
    fun `compose built-in and custom rules in single validation`() {
        BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.all() + ServiceTaskNamingRule())
            .validate()
            .assertNoErrors()
    }

    @Test
    fun `result escape hatch provides raw ValidationResult`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/valid-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION)
            .validate()
            .result()

        assertThat(result.isValid).isTrue()
    }
}
