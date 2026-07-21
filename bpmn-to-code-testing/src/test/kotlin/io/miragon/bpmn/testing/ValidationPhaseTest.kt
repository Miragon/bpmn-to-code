package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers how the validator orchestrates the validation phases: pre-merge single-model rules run
 * first and short-circuit on errors, and single- and cross-model rules compose in one chain.
 */
class ValidationPhaseTest {

    @Test
    fun `a pre-merge error short-circuits before the cross-model phase runs`() {
        val crossModelRule = RecordingCrossModelRule()

        BpmnValidator
            .fromClasspath("bpmn/order-fulfillment/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysFailingPreMergeRule(), crossModelRule)
            .validate()
            .assertViolationCount(1)
            .assertViolation(ruleId = "always-failing")

        // the cross-model phase never ran, so its rule was never invoked
        assertThat(crossModelRule.invoked).isFalse()
    }

    @Test
    fun `runs cross-model and built-in single-model rules together in one chain`() {
        BpmnValidator
            .fromClasspath("bpmn/order-fulfillment/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_MESSAGE_NAME, BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
            .validate()
            .assertNoViolations("missing-message-name")
            .assertViolation(ruleId = BpmnRules.CALL_ACTIVITY_TARGET_EXISTS.id)
    }

    private class AlwaysFailingPreMergeRule : SingleModelValidationRule {
        override val id = "always-failing"
        override val severity = Severity.ERROR

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            return listOf(
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = null,
                    processId = context.model.processId,
                    message = "always fails",
                ),
            )
        }
    }

    private class RecordingCrossModelRule : CrossModelValidationRule {
        override val id = "recording-cross-model"
        override val severity = Severity.ERROR

        var invoked = false
            private set

        override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
            invoked = true
            return emptyList()
        }
    }
}
