package io.miragon.bpmn.adapter.outbound.engine.bpmn

import io.miragon.bpmn.adapter.outbound.engine.xml.SecureBpmnParser
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.TimerType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.ReceiveTask
import org.junit.jupiter.api.Test

class EventDefinitionReaderTest {

    private val bpmnFile = requireNotNull(javaClass.getResource("/bpmn/event-definitions.bpmn")).readBytes()

    private val model = SecureBpmnParser.readModelFromBytes(bpmnFile)

    private val underTest = EventDefinitionReader

    @Test
    fun `reads a timer by its type`() {
        assertThat(definitionsOf("timer_date")).containsExactly(
            EventDefinitionInstance.Timer(timerType = TimerType.DATE, expression = "2026-01-01T00:00:00Z"),
        )
        assertThat(definitionsOf("timer_duration")).containsExactly(
            EventDefinitionInstance.Timer(timerType = TimerType.DURATION, expression = "PT15M"),
        )
        assertThat(definitionsOf("timer_cycle")).containsExactly(
            EventDefinitionInstance.Timer(timerType = TimerType.CYCLE, expression = "R3/PT10M"),
        )
        assertThat(definitionsOf("timer_untyped")).containsExactly(EventDefinitionInstance.Timer())
    }

    @Test
    fun `reads a message with the name of its root element`() {
        assertThat(definitionsOf("message_orderReceived")).containsExactly(
            EventDefinitionInstance.Message(MessageReference(messageRef = "Message_OrderReceived", messageName = "orderReceived")),
        )
        assertThat(definitionsOf("message_unreferenced")).containsExactly(EventDefinitionInstance.Message(MessageReference()))
    }

    @Test
    fun `reads signals, errors and escalations with their root element`() {
        assertThat(definitionsOf("signal_stockChanged")).containsExactly(
            EventDefinitionInstance.Signal(signalRef = "Signal_StockChanged", signalName = "stockChanged"),
        )
        assertThat(definitionsOf("error_paymentFailed")).containsExactly(
            EventDefinitionInstance.Error(errorRef = "Error_PaymentFailed", errorName = "paymentFailed", errorCode = "PAYMENT_FAILED"),
        )
        assertThat(definitionsOf("escalation_deliveryDelayed")).containsExactly(
            EventDefinitionInstance.Escalation(
                escalationRef = "Escalation_DeliveryDelayed",
                escalationName = "deliveryDelayed",
                escalationCode = "DELAYED",
            ),
        )
    }

    @Test
    fun `reads compensation, conditional, link and terminate definitions`() {
        assertThat(definitionsOf("compensation_shipOrder")).containsExactly(
            EventDefinitionInstance.Compensation(activityRef = "serviceTask_shipOrder", waitForCompletion = false),
        )
        assertThat(definitionsOf("conditional_stockLow")).containsExactly(
            EventDefinitionInstance.Conditional(expression = $$"${stockLow}"),
        )
        assertThat(definitionsOf("link_toShipping")).containsExactly(EventDefinitionInstance.Link(linkName = "toShipping"))
        assertThat(definitionsOf("terminate_orderCancelled")).containsExactly(EventDefinitionInstance.Terminate)
    }

    @Test
    fun `skips event definitions the domain has no instance for`() {
        assertThat(definitionsOf("cancel_orderCancelled")).isEmpty()
    }

    @Test
    fun `refers to a task's message like to an event's`() {
        val task = model.getModelElementById<ReceiveTask>("receiveTask_orderReceived")

        val reference = underTest.referenceOf(task.message)

        assertThat(reference).isEqualTo(MessageReference(messageRef = "Message_OrderReceived", messageName = "orderReceived"))
    }

    private fun definitionsOf(eventId: String): List<EventDefinitionInstance> {
        val event = model.getModelElementById<FlowNode>(eventId)
        return underTest.eventDefinitionsOf(event)
    }
}
