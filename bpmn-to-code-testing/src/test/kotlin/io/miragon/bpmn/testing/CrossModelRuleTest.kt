package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.junit.jupiter.api.Test

/**
 * Covers cross-model resolution: a [io.miragon.bpmn.domain.validation.CrossModelValidationRule] can
 * reach other loaded models via [io.miragon.bpmn.domain.validation.model.CrossModelValidationContext].
 *
 * The `order-fulfillment/` fixture is an isolated directory so that loading it as a set only ever
 * yields the parent process and its payment subprocess - see [BpmnValidator.fromClasspath].
 */
class CrossModelRuleTest {

    @Test
    fun `flags a call activity whose called process is absent from the loaded models`() {
        BpmnValidator
            .fromClasspath("order-fulfillment/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(TestRules.callActivityTargetExists())
            .validate()
            .assertViolation(
                ruleId = TestRules.CALL_ACTIVITY_TARGET_EXISTS,
                elementId = "CallActivity_ProcessPayment",
                messageContains = "paymentProcessing",
            )
    }

    @Test
    fun `passes when the called process is present among the loaded models`() {
        BpmnValidator
            .fromClasspath("order-fulfillment/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(TestRules.callActivityTargetExists())
            .validate()
            .assertNoViolations()
    }
}
