package io.miragon.bpmn.runtime.path

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.miragon.bpmn.runtime.path.example.OrderProcessApi.FlowNodes as MultiFlow

/**
 * Walks generated code where two sequence flows lead from one gateway to the same task: both share one
 * `SequenceFlows`, navigation stays a plain `then`, and a specific flow is picked by its condition.
 */
class MultiFlowSequenceFlowsTest {

    @Test
    fun `several flows to the same element share one transition`() {
        val approve = MultiFlow.GatewayAmount.next.taskApprove

        assertThat(approve.target).isEqualTo(MultiFlow.TaskApprove)
        assertThat(approve.flows.map { it.conditionExpression }).containsExactly("=amount < 100", "=customer.isVip")
        assertThat(MultiFlow.GatewayAmount.next.taskReview.flow.isDefault).isTrue()
    }

    @Test
    fun `then onto a shared transition records the element but no ambiguous flow`() {
        val path = ProcessPath.from(MultiFlow.GatewayAmount).then { it.taskApprove }

        assertThat(path.ids).containsExactly("gateway_amount", "task_approve")
        assertThat(path.flowIds).isEmpty()
    }

    @Test
    fun `then along a picked flow records exactly that flow`() {
        val path = ProcessPath.from(MultiFlow.GatewayAmount)
            .then { next -> next.taskApprove.flows.single { it.conditionExpression == "=customer.isVip" } }

        assertThat(path.ids).containsExactly("gateway_amount", "task_approve")
        assertThat(path.flowIds).containsExactly("flow_vip")
    }
}
