package io.miragon.bpmn.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessGraphTest {

    private val gateway = FlowNodeDefinition.Gateway(
        id = "gateway",
        kind = GatewayKind.EXCLUSIVE,
        outgoing = listOf("flow_no", "flow_yes", "flow_unresolvable"),
    )

    private val underTest = ProcessGraph(
        flowNodes = listOf(
            gateway,
            FlowNodeDefinition.Unknown(id = "yes", incoming = listOf("flow_yes")),
            FlowNodeDefinition.Unknown(id = "no", incoming = listOf("flow_no")),
        ),
        sequenceFlows = listOf(
            SequenceFlowDefinition(id = "flow_yes", sourceRef = "gateway", targetRef = "yes", isDefault = true),
            SequenceFlowDefinition(id = "flow_no", sourceRef = "gateway", targetRef = "no", conditionExpression = "=stock = 0"),
            SequenceFlowDefinition(id = null, sourceRef = "gateway", targetRef = "yes"),
        ),
    )

    @Test
    fun `outgoingFlowsOf keeps the node's declaration order and skips flows it cannot resolve`() {
        val flows = underTest.outgoingFlowsOf(gateway)

        assertThat(flows.map { it.id }).containsExactly("flow_no", "flow_yes")
        assertThat(flows.first().conditionExpression).isEqualTo("=stock = 0")
        assertThat(flows.last().isDefault).isTrue()
    }

    @Test
    fun `followingElementsOf resolves the targets of the outgoing flows`() {
        assertThat(underTest.followingElementsOf(gateway)).containsExactly("no", "yes")
    }
}
