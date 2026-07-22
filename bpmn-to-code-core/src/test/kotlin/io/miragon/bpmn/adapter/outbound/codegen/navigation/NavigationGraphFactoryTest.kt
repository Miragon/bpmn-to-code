package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.adapter.outbound.codegen.builder.buildSubscribeNewsletterFlowNodes
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavGraph.NavNode
import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.TaskKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NavigationGraphFactoryTest {

    private val flowNodes = buildSubscribeNewsletterFlowNodes(
        confirmationMailImpl = "#{sendConfirmation}",
        welcomeMailImpl = "#{sendWelcome}",
        registrationCompletedImpl = "newsletter.completed",
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

        // given: SubProcess_Confirmation follows into SendWelcomeMail and has two boundary events attached
        assertThat(graph.node("subProcessConfirmation").successors.map { it.propertyName })
            .containsExactly("activitySendWelcomeMail", "errorEventInvalidMail", "timerAfter3Days")

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

    @Test
    fun `colliding normalized names are disambiguated deterministically and idempotently`() {
        // given: two distinct ids that normalize to the same constant name
        val nodes = listOf(
            FlowNodeDefinition(id = "the-task", nodeType = BpmnNodeType.Activity.Task(TaskKind.SERVICE)),
            FlowNodeDefinition(id = "the_task", nodeType = BpmnNodeType.Activity.Task(TaskKind.SERVICE)),
            FlowNodeDefinition(id = "start", nodeType = BpmnNodeType.Event(EventShape.START_EVENT)),
        )

        val first = NavigationGraphFactory.build(nodes).nodes.map { it.objectName }
        val second = NavigationGraphFactory.build(nodes).nodes.map { it.objectName }

        assertThat(first).doesNotHaveDuplicates()
        assertThat(first).isEqualTo(second)
        assertThat(first).contains("TheTask", "TheTask2")
    }

    private fun NavGraph.node(propertyName: String): NavNode {
        return nodes.single { it.propertyName == propertyName }
    }
}
