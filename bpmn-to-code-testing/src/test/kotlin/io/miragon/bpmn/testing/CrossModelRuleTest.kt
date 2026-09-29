package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.junit.jupiter.api.Test

/**
 * Covers cross-model resolution: a [io.miragon.bpmn.domain.validation.CrossModelValidationRule] can
 * reach other loaded models via [io.miragon.bpmn.domain.validation.model.CrossModelValidationContext].
 *
 * Loading a single file isolates one model, loading `bpmn/c7/` resolves the shared models against each
 * other. The `bpmn/message-flow/` fixture is an isolated directory because no shared model catches the
 * message thrown by `cancelBikeOrder` - see [BpmnValidator.fromClasspath].
 */
class CrossModelRuleTest {

    @Test
    fun `flags a call activity whose called process is absent from the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/bike-leasing.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.CALL_ACTIVITY_TARGET_EXISTS.id,
                elementId = "callActivity_cancelBikeOrder",
                messageContains = "cancelBikeOrder",
            )
    }

    @Test
    fun `passes when the called process is present among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
            .validate()
            .assertNoViolations()
    }

    @Test
    fun `warns when a thrown message has no catcher among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/cancel-bike-order.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_MESSAGE_THROW)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNCAUGHT_MESSAGE_THROW.id,
                elementId = "endEvent_bikeOrderCancelled",
                messageContains = "miravelo.bikeOrderCancelled",
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
            .fromClasspath("bpmn/c7/membership.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_SIGNAL_THROW)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNCAUGHT_SIGNAL_THROW.id,
                elementId = "endEvent_membershipActivated",
                messageContains = "miravelo.memberActivated",
            )
    }

    @Test
    fun `passes when the thrown signal is caught by another loaded model`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNCAUGHT_SIGNAL_THROW)
            .validate()
            .assertNoViolations()
    }

    @Test
    fun `warns when a caught signal is never thrown among the loaded models`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/welcome-package.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNPUBLISHED_SIGNAL_CATCH)
            .validate()
            .assertViolation(
                ruleId = BpmnRules.UNPUBLISHED_SIGNAL_CATCH.id,
                elementId = "startEvent_memberActivated",
                messageContains = "miravelo.memberActivated",
            )
    }

    @Test
    fun `passes when the caught signal is thrown by another loaded model`() {
        BpmnValidator
            .fromClasspath("bpmn/c7/")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.UNPUBLISHED_SIGNAL_CATCH)
            .validate()
            .assertNoViolations()
    }
}
