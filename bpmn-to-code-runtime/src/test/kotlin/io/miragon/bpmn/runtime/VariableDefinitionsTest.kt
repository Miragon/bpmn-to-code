package io.miragon.bpmn.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VariableDefinitionsTest {

    private object OrderVariables : RegisteredVariableDefinitions() {
        val CUSTOMER_ID = input("customerId")
        val ORDER_ID = output("orderId")
        val TICKET = inOut("ticket")
    }

    private object ShipmentVariables : VariableDefinitions() {
        override val all: List<VariableName> = listOf(VariableName.Output("customerId"), VariableName.Input("trackingId"))
    }

    private object EmptyVariables : RegisteredVariableDefinitions()

    private class NodeWithVariables(id: String, override val variables: VariableDefinitions) :
        AbstractFlowNode(ElementId(id), BpmnElementType.SERVICE_TASK),
        HasVariables

    @Test
    fun `registered variables keep their direction and value`() {
        assertThat(OrderVariables.CUSTOMER_ID).isEqualTo(VariableName.Input("customerId"))
        assertThat(OrderVariables.ORDER_ID).isEqualTo(VariableName.Output("orderId"))
        assertThat(OrderVariables.TICKET).isEqualTo(VariableName.InOut("ticket"))
    }

    @Test
    fun `all lists the registered variables in declaration order`() {
        assertThat(OrderVariables.all).containsExactly(OrderVariables.CUSTOMER_ID, OrderVariables.ORDER_ID, OrderVariables.TICKET)
        assertThat(EmptyVariables.all).isEmpty()
    }

    @Test
    fun `all hands out a copy that cannot change the registered variables`() {
        val handedOut = OrderVariables.all

        (handedOut as? MutableList<VariableName>)?.runCatching { add(VariableName.Input("intruder")) }

        assertThat(OrderVariables.all).hasSize(3)
    }

    @Test
    fun `inputs are the variables a node reads, in-out included`() {
        assertThat(OrderVariables.inputs).containsExactly(OrderVariables.CUSTOMER_ID, OrderVariables.TICKET)
        assertThat(ShipmentVariables.inputs).containsExactly(VariableName.Input("trackingId"))
    }

    @Test
    fun `outputs are the variables a node writes, in-out included`() {
        assertThat(OrderVariables.outputs).containsExactly(OrderVariables.ORDER_ID, OrderVariables.TICKET)
        assertThat(ShipmentVariables.outputs).containsExactly(VariableName.Output("customerId"))
    }

    @Test
    fun `distinct variables of nodes list each variable name once, whatever its direction`() {
        val nodes: List<FlowNode> = listOf(
            NodeWithVariables("placeOrder", OrderVariables),
            object : AbstractFlowNode(ElementId("gateway"), BpmnElementType.EXCLUSIVE_GATEWAY) {},
            NodeWithVariables("shipOrder", ShipmentVariables),
        )

        assertThat(HasVariables.distinctVariablesOf(nodes)).containsExactly("customerId", "orderId", "ticket", "trackingId")
    }
}
