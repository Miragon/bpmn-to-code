package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBpmnModel
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CallActivityTargetExistsRuleTest {

    private val underTest = CallActivityTargetExistsRule()

    private fun caller(processId: String, callId: String, calledElement: String?) = testBpmnModel(
        processId = processId,
        flowNodes = listOf(
            FlowNodeDefinition(
                id = callId,
                properties = FlowNodeProperties.CallActivity(CallActivityDefinition(id = callId, calledElement = calledElement)),
            ),
        ),
    )

    @Test
    fun `reports error when the called process is absent from the loaded models`() {

        // given: a caller referencing a process that was not loaded
        val model = caller(processId = "orderFulfillment", callId = "call1", calledElement = "paymentProcessing")

        // when
        val violations = underTest.validate(CrossModelValidationContext(models = listOf(model), engine = ProcessEngine.CAMUNDA_7))

        // then: a single ERROR naming the unknown process
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("call1")
        assertThat(violations[0].message).contains("paymentProcessing")
    }

    @Test
    fun `no violations when the called process is present among the loaded models`() {

        // given: both the caller and the called process are loaded
        val caller = caller(processId = "orderFulfillment", callId = "call1", calledElement = "paymentProcessing")
        val called = testBpmnModel(processId = "paymentProcessing")

        // when
        val violations = underTest.validate(CrossModelValidationContext(models = listOf(caller, called), engine = ProcessEngine.CAMUNDA_7))

        // then
        assertThat(violations).isEmpty()
    }

    @Test
    fun `ignores call activities without a called element`() {

        // given: a call activity with no called element - the concern of MissingCalledElementRule
        val model = caller(processId = "orderFulfillment", callId = "call1", calledElement = null)

        // when
        val violations = underTest.validate(CrossModelValidationContext(models = listOf(model), engine = ProcessEngine.CAMUNDA_7))

        // then
        assertThat(violations).isEmpty()
    }
}
