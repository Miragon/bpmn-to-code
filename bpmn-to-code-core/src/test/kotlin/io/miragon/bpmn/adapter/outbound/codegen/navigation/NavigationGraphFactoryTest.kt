package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.adapter.outbound.codegen.builder.buildSubscribeNewsletterFlowNodes
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph.NavigationNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NavigationGraphFactoryTest {

    private val flowNodes = buildSubscribeNewsletterFlowNodes(
        confirmationMailImpl = "#{sendConfirmation}",
        welcomeMailImpl = "#{sendWelcome}",
        registrationCompletedImpl = "newsletter.completed",
        notifyCommunityImpl = "newsletter.notifyCommunity",
    )

    @Test
    fun `root scope contains only top-level nodes and keeps subprocess children nested`() {
        val graph = NavigationGraphFactory.build(flowNodes)

        // given: five nodes declare parentId = SubProcess_Confirmation -> they live in the inner scope, not root
        assertThat(graph.nodes.map { it.propertyName })
            .contains("subProcessConfirmation", "startEventSubmitRegistrationForm", "serviceTaskIncrementSubscriptionCounter")
            .doesNotContain("activityConfirmRegistration", "startEventRequestReceived", "timerEveryDay")

        val subProcess = graph.node("subProcessConfirmation")
        assertThat(subProcess.inner).isNotNull
        assertThat(subProcess.inner!!.nodes.map { it.propertyName })
            .containsExactlyInAnyOrder(
                "activityConfirmRegistration",
                "activitySendConfirmationMail",
                "endEventSubscriptionConfirmed",
                "startEventRequestReceived",
                "timerEveryDay",
            )
    }

    @Test
    fun `sequence-flow and boundary edges are unified as target-named successors`() {
        val graph = NavigationGraphFactory.build(flowNodes)

        // given: SubProcess_Confirmation follows into the notification split gateway and has two boundary events attached
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
        val graph = NavigationGraphFactory.build(flowNodes)
        val inner = graph.node("subProcessConfirmation").inner!!

        assertThat(inner.node("startEventRequestReceived").isStart).isTrue()
        assertThat(inner.node("startEventRequestReceived").successors.map { it.propertyName })
            .containsExactly("activitySendConfirmationMail")
        assertThat(inner.node("activityConfirmRegistration").successors.map { it.propertyName })
            .containsExactly("endEventSubscriptionConfirmed", "timerEveryDay")
    }

    @Test
    fun `call activity stays opaque but exposes the called process id as info`() {
        val graph = NavigationGraphFactory.build(flowNodes)
        val callActivity = graph.node("callActivityAbortRegistration")

        assertThat(callActivity.inner).isNull()
        assertThat(callActivity.calledProcessId).isEqualTo("abort-registration")
        assertThat(callActivity.successors.map { it.propertyName })
            .containsExactly("compensationEndEventRegistrationAborted")
    }

    @Test
    fun `node exposes id, flat element type and optional display name`() {
        val graph = NavigationGraphFactory.build(flowNodes)
        val serviceTask = graph.node("serviceTaskIncrementSubscriptionCounter")

        assertThat(serviceTask.id).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(serviceTask.elementType).isEqualTo("SERVICE_TASK")
        assertThat(serviceTask.objectName).isEqualTo("ServiceTaskIncrementSubscriptionCounter")
        assertThat(serviceTask.name).isNull()   // no displayName in the model

        // ActivityConfirmRegistration declares displayName "Confirm registration"
        val confirm = graph.node("subProcessConfirmation").inner!!.node("activityConfirmRegistration")
        assertThat(confirm.name).isEqualTo("Confirm registration")
    }

    private fun NavigationGraph.node(propertyName: String): NavigationNode {
        return nodes.single { it.propertyName == propertyName }
    }
}
