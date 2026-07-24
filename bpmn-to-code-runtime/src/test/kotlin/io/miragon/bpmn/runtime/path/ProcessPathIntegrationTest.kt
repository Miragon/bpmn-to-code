package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Relations as Newsletter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Exercises [ProcessPath] over the *actually generated* Newsletter API (compiled here as a test source) —
 * which also proves the generated `: Navigable<Next>` / `: FlowNode` code compiles against the runtime
 * interfaces. Newsletter is the single navigation fixture: subprocess, boundary events, call activity and a
 * parallel (AND) split/join around the notification tasks. The [ProcessPathTest] sibling covers the same step
 * operators over hand-built stubs.
 */
class ProcessPathIntegrationTest {

    @Test
    fun `sequential path leaves a subprocess via continueAfter and records the passed elements in order`() {
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .skip { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .then { it.endEventSubscriptionConfirmed }
            .continueAfter(Newsletter.SubProcessConfirmation) { it.gatewaySplitNotifications }
            .then { it.activitySendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }

        assertThat(path.ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "EndEvent_SubscriptionConfirmed",
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted",
        )
    }

    @Test
    fun `inside walks the subprocess interior then continues on the subprocess node`() {
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .skip { it.subProcessConfirmation }
            .inside {
                enter { it.startEventRequestReceived }
                    .then { it.activitySendConfirmationMail }
                    .then { it.activityConfirmRegistration }
                    .then { it.endEventSubscriptionConfirmed }
            }
            .then { it.gatewaySplitNotifications }
            .then { it.activitySendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }

        assertThat(path.ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "EndEvent_SubscriptionConfirmed",
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted",
        )
    }

    @Test
    fun `interruptedBy leaves the subprocess through a boundary timer and records it`() {
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .skip { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.timerAfter3Days }
            .then { it.callActivityAbortRegistration }

        assertThat(path.ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_After3Days",
            "CallActivity_AbortRegistration",
        )
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the parallel fork to walk the second branch in one chain`() {
        val passed = ProcessPath.from(Newsletter.gatewaySplitNotifications)
            .then { it.activitySendWelcomeMail }
            .jumpTo(Newsletter.GatewaySplitNotifications)
            .then { it.activityNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes

        assertThat(passed.map { it.id.value }).containsExactly(
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Activity_NotifyCommunity",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted",
        )
    }

    @Test
    fun `nodesOf unions the parallel branches into a deduplicated set`() {
        val welcomeBranch = ProcessPath.from(Newsletter.gatewaySplitNotifications)
            .then { it.activitySendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes
        val notifyBranch = ProcessPath.from(Newsletter.gatewaySplitNotifications)
            .then { it.activityNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes

        val passed = nodesOf(welcomeBranch, notifyBranch).map { it.id.value }

        assertThat(passed)
            .contains("Activity_SendWelcomeMail", "Activity_NotifyCommunity", "Gateway_JoinNotifications")
            .doesNotHaveDuplicates()
    }

    @Test
    fun `nodes expose FlowNode metadata`() {
        assertThat(Newsletter.gatewaySplitNotifications.id.value).isEqualTo("Gateway_SplitNotifications")
        assertThat(Newsletter.gatewaySplitNotifications.elementType).isEqualTo("PARALLEL_GATEWAY")
    }
}
