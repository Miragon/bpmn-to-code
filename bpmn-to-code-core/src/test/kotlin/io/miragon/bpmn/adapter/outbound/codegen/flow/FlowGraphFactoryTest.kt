package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.builder.buildSubscribeNewsletterFlowNodes
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowGraphFactoryTest {

    private val processGraph = testSubscribeNewsletterModel(
        flowNodes = buildSubscribeNewsletterFlowNodes(
            confirmationMailImpl = "#{sendConfirmation}",
            welcomeMailImpl = "#{sendWelcome}",
            registrationCompletedImpl = "newsletter.completed",
            notifyCommunityImpl = "newsletter.notifyCommunity",
        ),
    ).graph

    @Test
    fun `root scope contains only top-level nodes and keeps subprocess children nested`() {
        val graph = FlowGraphFactory.build(processGraph)

        // given: five nodes declare parentId = subProcess_confirmation -> they live in the inner scope, not root
        assertThat(graph.nodes.map { it.propertyName })
            .contains("subProcessConfirmation", "startEventSubmitRegistrationForm", "serviceTaskIncrementSubscriptionCounter")
            .doesNotContain("userTaskConfirmRegistration", "startEventRequestReceived", "timerEveryDay")

        val subProcess = graph.node("subProcessConfirmation")
        assertThat(subProcess.inner).isNotNull
        assertThat(subProcess.inner!!.nodes.map { it.propertyName })
            .containsExactlyInAnyOrder(
                "userTaskConfirmRegistration",
                "serviceTaskSendConfirmationMail",
                "endEventSubscriptionConfirmed",
                "startEventRequestReceived",
                "timerEveryDay",
            )
    }

    @Test
    fun `sequence-flow and boundary edges are unified as target-named successors`() {
        val graph = FlowGraphFactory.build(processGraph)

        // given: subProcess_confirmation follows into the notification split gateway and has two boundary events attached
        assertThat(graph.node("subProcessConfirmation").successors.map { it.propertyName })
            .containsExactly("errorEventInvalidMail", "gatewaySplitNotifications", "timerAfter3Days")

        // and: the service task follows into the subprocess and carries a compensation boundary
        assertThat(graph.node("serviceTaskIncrementSubscriptionCounter").successors.map { it.propertyName })
            .containsExactly("compensationEventOnSubscriptionCounter", "subProcessConfirmation")

        // boundary event is itself a node whose successor is the escape target
        assertThat(graph.node("errorEventInvalidMail").successors.map { it.propertyName })
            .containsExactly("endEventRegistrationNotPossible")
    }

    @Test
    fun `inner scope resolves its own start event and edges`() {
        val graph = FlowGraphFactory.build(processGraph)
        val inner = graph.node("subProcessConfirmation").inner!!

        assertThat(inner.node("startEventRequestReceived").isStart).isTrue()
        assertThat(inner.node("startEventRequestReceived").successors.map { it.propertyName })
            .containsExactly("serviceTaskSendConfirmationMail")
        assertThat(inner.node("userTaskConfirmRegistration").successors.map { it.propertyName })
            .containsExactly("endEventSubscriptionConfirmed", "timerEveryDay")
    }

    @Test
    fun `call activity stays opaque but exposes the called process id as info`() {
        val graph = FlowGraphFactory.build(processGraph)
        val callActivity = graph.node("callActivityAbortRegistration")

        assertThat(callActivity.inner).isNull()
        assertThat(callActivity.calledProcessId).isEqualTo("abort-registration")
        assertThat(callActivity.successors.map { it.propertyName })
            .containsExactly("compensationEndEventRegistrationAborted")
    }

    @Test
    fun `node exposes id, flat element type and optional display name`() {
        val graph = FlowGraphFactory.build(processGraph)
        val serviceTask = graph.node("serviceTaskIncrementSubscriptionCounter")

        assertThat(serviceTask.id).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(serviceTask.elementType).isEqualTo("SERVICE_TASK")
        assertThat(serviceTask.objectName).isEqualTo("ServiceTaskIncrementSubscriptionCounter")
        assertThat(serviceTask.name).isNull() // no displayName in the model

        // userTaskConfirmRegistration declares displayName "Confirm registration"
        val confirm = graph.node("subProcessConfirmation").inner!!.node("userTaskConfirmRegistration")
        assertThat(confirm.name).isEqualTo("Confirm registration")
    }

    private fun FlowGraph.node(propertyName: String): FlowGraphNode = nodes.single { it.propertyName == propertyName }
}
