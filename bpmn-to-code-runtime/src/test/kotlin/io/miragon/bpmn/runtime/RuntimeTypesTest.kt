package io.miragon.bpmn.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuntimeTypesTest {

    @Test
    fun `identifier wrappers expose value via toString`() {
        assertThat(ProcessId("order-process").toString()).isEqualTo("order-process")
        assertThat(ElementId("place-order").toString()).isEqualTo("place-order")
        assertThat(MessageName("OrderPlaced").toString()).isEqualTo("OrderPlaced")
        assertThat(SignalName("CancelRequested").toString()).isEqualTo("CancelRequested")
    }

    @Test
    fun `identifier wrappers expose the raw value property`() {
        assertThat(ProcessId("order-process").value).isEqualTo("order-process")
        assertThat(ElementId("place-order").value).isEqualTo("place-order")
        assertThat(MessageName("OrderPlaced").value).isEqualTo("OrderPlaced")
        assertThat(SignalName("CancelRequested").value).isEqualTo("CancelRequested")
    }

    @Test
    fun `identifier wrappers implement value equality`() {
        assertThat(ProcessId("a")).isEqualTo(ProcessId("a"))
        assertThat(ProcessId("a")).isNotEqualTo(ProcessId("b"))
        assertThat(ProcessId("a").hashCode()).isEqualTo(ProcessId("a").hashCode())
    }

    @Test
    fun `VariableName subtypes preserve direction and value`() {
        val input: VariableName = VariableName.Input("customerId")
        val output: VariableName = VariableName.Output("orderId")
        val inOut: VariableName = VariableName.InOut("ticket")

        assertThat(input.value).isEqualTo("customerId")
        assertThat(output.value).isEqualTo("orderId")
        assertThat(inOut.value).isEqualTo("ticket")

        assertThat(input.toString()).isEqualTo("customerId")
        assertThat(output.toString()).isEqualTo("orderId")
        assertThat(inOut.toString()).isEqualTo("ticket")
    }

    @Test
    fun `VariableName direction subtypes are distinct types for the same value`() {
        assertThat(VariableName.Input("x")).isNotEqualTo(VariableName.Output("x"))
        assertThat(VariableName.Input("x")).isNotEqualTo(VariableName.InOut("x"))
    }

    @Test
    fun `BpmnEngine covers all supported dialects`() {
        assertThat(BpmnEngine.entries).containsExactly(
            BpmnEngine.ZEEBE,
            BpmnEngine.CAMUNDA_7,
            BpmnEngine.OPERATON,
        )
    }

    @Test
    fun `BpmnTimer, BpmnError, BpmnEscalation carry their pair of strings`() {
        val timer = BpmnTimer("Duration", "PT5M")
        assertThat(timer.type).isEqualTo("Duration")
        assertThat(timer.timerValue).isEqualTo("PT5M")

        val error = BpmnError("NotFound", "E_404")
        assertThat(error.name).isEqualTo("NotFound")
        assertThat(error.code).isEqualTo("E_404")

        val escalation = BpmnEscalation("OutOfHours", "E_HRS")
        assertThat(escalation.name).isEqualTo("OutOfHours")
        assertThat(escalation.code).isEqualTo("E_HRS")
    }

    @Test
    fun `AbstractFlowNode derives identity and hash from the element id`() {
        val node = flowNode("approve-task")
        val same = flowNode("approve-task")
        val other = flowNode("reject-task")

        assertThat(node).isEqualTo(same)
        assertThat(node).isNotEqualTo(other)
        assertThat(node.hashCode()).isEqualTo(ElementId("approve-task").hashCode())
        assertThat(node.hashCode()).isEqualTo(same.hashCode())
    }

    @Test
    fun `AbstractFlowNode exposes the display name and defaults it to null`() {
        val named = object : AbstractFlowNode(ElementId("approve-task"), "USER_TASK", "Approve order") {}

        assertThat(named.name).isEqualTo("Approve order")
        assertThat(flowNode("approve-task").name).isNull()
    }

    @Test
    fun `AbstractFlowNode equality ignores the display name`() {
        val named = object : AbstractFlowNode(ElementId("approve-task"), "USER_TASK", "Approve order") {}

        assertThat(named).isEqualTo(flowNode("approve-task"))
        assertThat(named.hashCode()).isEqualTo(flowNode("approve-task").hashCode())
    }

    private fun flowNode(id: String): AbstractFlowNode = object : AbstractFlowNode(ElementId(id), "SERVICE_TASK") {}

    @Test
    fun `SequenceFlow carries id, name, condition, default marker and its typed target`() {
        val target = flowNode("end")
        val flow = SequenceFlow(ElementId("flow_1"), "No", "=stock > 0", false, target)

        assertThat(flow.id).isEqualTo(ElementId("flow_1"))
        assertThat(flow.name).isEqualTo("No")
        assertThat(flow.conditionExpression).isEqualTo("=stock > 0")
        assertThat(flow.isDefault).isFalse()
        assertThat(flow.target).isSameAs(target)
    }

    @Test
    fun `SequenceFlow implements value equality and copy`() {
        val flow = SequenceFlow(ElementId("flow_1"), null, null, true, flowNode("end"))

        assertThat(flow).isEqualTo(SequenceFlow(ElementId("flow_1"), null, null, true, flowNode("end")))
        assertThat(flow).isNotEqualTo(flow.copy(isDefault = false))
        assertThat(flow.copy(name = "Yes").name).isEqualTo("Yes")
        assertThat(flow.hashCode()).isEqualTo(SequenceFlow(ElementId("flow_1"), null, null, true, flowNode("end")).hashCode())
    }

    @Test
    fun `HasFlows exposes the node's typed edges holder`() {
        val end = flowNode("end")
        val start = object : AbstractFlowNode(ElementId("start"), "START_EVENT"), HasFlows<SequenceFlow<AbstractFlowNode>> {
            override fun flows(): SequenceFlow<AbstractFlowNode> = SequenceFlow(ElementId("flow_1"), null, "= ok", false, end)
        }

        assertThat(start.flows().target).isEqualTo(end)
        assertThat(start.flows().conditionExpression).isEqualTo("= ok")
    }

    @Test
    fun `InputOutputMapping keeps target plus source or sourceExpression`() {
        val plain = InputOutputMapping(target = "childSubscriptionId", source = "subscriptionId")
        val expression = InputOutputMapping(target = "childReasonCode", sourceExpression = "\${reasonCode}")

        assertThat(plain.target).isEqualTo("childSubscriptionId")
        assertThat(plain.source).isEqualTo("subscriptionId")
        assertThat(plain.sourceExpression).isNull()

        assertThat(expression.sourceExpression).isEqualTo("\${reasonCode}")
        assertThat(expression.source).isNull()
    }

    @Test
    fun `InputOutputMapping defaults source and sourceExpression to null and exposes target via toString`() {
        val mapping = InputOutputMapping(target = "abortResult")
        assertThat(mapping.source).isNull()
        assertThat(mapping.sourceExpression).isNull()
        assertThat(mapping.toString()).isEqualTo("abortResult")
    }
}
