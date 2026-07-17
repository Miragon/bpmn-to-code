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

class CrossModelValidationRuleTest {

    /**
     * Cross-process rule: every call activity must reference a process that exists among the loaded models.
     */
    private class CallActivityTargetExistsRule : CrossModelValidationRule {
        override val id = "call-activity-target-exists"
        override val severity = Severity.ERROR

        override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
            return context.models.flatMap { model ->
                model.callActivities
                    .filter { it.hasCalledElement() && context.resolveCalledModel(it) == null }
                    .map { callActivity ->
                        ValidationViolation(
                            ruleId = id,
                            severity = severity,
                            elementId = callActivity.id,
                            processId = model.processId,
                            message = "Call activity '${callActivity.id}' references unknown process '${callActivity.getValue()}'.",
                        )
                    }
            }
        }
    }

    @Test
    fun `flags a call activity whose called process is absent from the loaded models`() {

        // when: validating with the cross-process rule (only order-fulfillment is loaded, not its payment subprocess)
        val result = BpmnValidator
            .fromClasspath("crossmodel/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(CallActivityTargetExistsRule())
            .validate()
            .result()

        // then: the dangling call activity is reported
        assertThat(result.violations).hasSize(1)
        val violation = result.violations.single()
        assertThat(violation.ruleId).isEqualTo("call-activity-target-exists")
        assertThat(violation.elementId).isEqualTo("CallActivity_ProcessPayment")
        assertThat(violation.message).contains("paymentProcessing")
    }

    @Test
    fun `passes when the called process is present among the loaded models`() {

        // when: validating with the cross-process rule (both order-fulfillment and payment-processing are loaded)
        val result = BpmnValidator
            .fromClasspath("crossmodel/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(CallActivityTargetExistsRule())
            .validate()
            .result()

        // then: the call activity resolves and no violation is reported
        assertThat(result.violations).isEmpty()
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

    @Test
    fun `a pre-merge error short-circuits before the cross-model phase runs`() {
        val crossModelRule = RecordingCrossModelRule()

        // when: a pre-merge single-model rule reports an ERROR
        val result = BpmnValidator
            .fromClasspath("crossmodel/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(AlwaysFailingPreMergeRule(), crossModelRule)
            .validate()
            .result()

        // then: validation stops before the cross-model phase, so the cross-model rule never runs
        assertThat(crossModelRule.invoked).isFalse()
        assertThat(result.violations.map { it.ruleId }).containsExactly("always-failing")
    }

    @Test
    fun `runs cross-model and built-in single-model rules together in one chain`() {

        // when: mixing a built-in single-model rule with a cross-model rule
        val validationAssert = BpmnValidator
            .fromClasspath("crossmodel/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.MISSING_MESSAGE_NAME, CallActivityTargetExistsRule())
            .validate()

        // then: the single-model rule passes and the cross-model rule fires
        validationAssert.assertNoViolations("missing-message-name")
        assertThat(validationAssert.result().violations.map { it.ruleId })
            .contains("call-activity-target-exists")
    }
}
