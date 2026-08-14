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
    fun `BpmnFlow defaults nullable fields to null`() {
        val flow = BpmnFlow(id = "f1", sourceRef = "s", targetRef = "t")
        assertThat(flow.id).isEqualTo("f1")
        assertThat(flow.sourceRef).isEqualTo("s")
        assertThat(flow.targetRef).isEqualTo("t")
        assertThat(flow.name).isNull()
        assertThat(flow.condition).isNull()
        assertThat(flow.isDefault).isFalse()
    }

    @Test
    fun `BpmnFlow retains a default flag and its labelled fields`() {
        val flow = BpmnFlow(
            id = "f2",
            name = "yes",
            sourceRef = "gateway",
            targetRef = "approve",
            condition = "\${approved}",
            isDefault = true,
        )
        assertThat(flow.name).isEqualTo("yes")
        assertThat(flow.condition).isEqualTo("\${approved}")
        assertThat(flow.isDefault).isTrue()
    }

    @Test
    fun `BpmnRelations retains list and nullable fields`() {
        val relations = BpmnRelations(
            name = "Approve",
            previousElements = listOf("start"),
            followingElements = listOf("end"),
            parentId = null,
            attachedToRef = null,
            attachedElements = listOf("boundary-timer"),
            elementType = "USER_TASK",
        )
        assertThat(relations.name).isEqualTo("Approve")
        assertThat(relations.parentId).isNull()
        assertThat(relations.attachedToRef).isNull()
        assertThat(relations.previousElements).containsExactly("start")
        assertThat(relations.followingElements).containsExactly("end")
        assertThat(relations.attachedElements).containsExactly("boundary-timer")
        assertThat(relations.elementType).isEqualTo("USER_TASK")
    }

    @Test
    fun `BpmnRelations exposes parent and boundary host for nested elements`() {
        val relations = BpmnRelations(
            name = "Send reminder",
            previousElements = listOf("start"),
            followingElements = listOf("end"),
            parentId = "confirmation-subprocess",
            attachedToRef = "confirm-task",
            attachedElements = emptyList(),
            elementType = "TIMER_BOUNDARY_EVENT",
        )
        assertThat(relations.parentId).isEqualTo("confirmation-subprocess")
        assertThat(relations.attachedToRef).isEqualTo("confirm-task")
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

    private fun flowNode(id: String): AbstractFlowNode = object : AbstractFlowNode(ElementId(id), "SERVICE_TASK") {}

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
