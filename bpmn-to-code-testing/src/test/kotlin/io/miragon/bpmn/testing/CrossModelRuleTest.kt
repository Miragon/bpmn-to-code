package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.junit.jupiter.api.Test

/**
 * Covers cross-model resolution: a [io.miragon.bpmn.domain.validation.CrossModelValidationRule] can
 * reach other loaded models via [io.miragon.bpmn.domain.validation.model.CrossModelValidationContext].
 *
 * The `bpmn/order-fulfillment/` fixture is an isolated directory so that loading it as a set only
 * ever yields the parent process and its payment subprocess - see [BpmnValidator.fromClasspath].
 */
class CrossModelRuleTest {

    @Test
    fun `flags a call activity whose called process is absent from the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/order-fulfillment/order-fulfillment.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.CALL_ACTIVITY_TARGET_EXISTS.id,
                elementId = "CallActivity_ProcessPayment",
                messageContains = "paymentProcessing",
            )
    }

    @Test
    fun `passes when the called process is present among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/order-fulfillment/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
            .validate()
            .assertNoViolations()
    }

    @Test
    fun `warns when a thrown message has no catcher among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/message-flow/order-shipping.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_MESSAGE_THROW)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNCAUGHT_MESSAGE_THROW.id,
                elementId = "EndEvent_OrderShipped",
                messageContains = "OrderShipped",
            )
    }

    @Test
    fun `passes when the thrown message is caught by another loaded model`() {
        BpmnValidator
            .fromClasspath("bpmn/message-flow/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_MESSAGE_THROW)
            .validate()
            .assertNoViolations()
    }

    @Test
    fun `warns when a thrown signal has no subscriber among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/signal-flow/registration-blocked.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_SIGNAL_THROW)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNCAUGHT_SIGNAL_THROW.id,
                elementId = "EndEvent_RegistrationBlocked",
                messageContains = "RegistrationBlocked",
            )
    }

    @Test
    fun `passes when the thrown signal is caught by another loaded model`() {
        BpmnValidator
            .fromClasspath("bpmn/signal-flow/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_SIGNAL_THROW)
            .validate()
            .assertNoViolations()
    }

    @Test
    fun `warns when a caught signal is never thrown among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/signal-flow/registration-monitor.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNPUBLISHED_SIGNAL_CATCH)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNPUBLISHED_SIGNAL_CATCH.id,
                elementId = "StartEvent_RegistrationBlocked",
                messageContains = "RegistrationBlocked",
            )
    }

    @Test
    fun `passes when the caught signal is thrown by another loaded model`() {
        BpmnValidator
            .fromClasspath("bpmn/signal-flow/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNPUBLISHED_SIGNAL_CATCH)
            .validate()
            .assertNoViolations()
    }
}
