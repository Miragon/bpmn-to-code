package io.miragon.bpmn.runtime.path

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Relations as Newsletter

/**
 * Exercises the fluent [PathWalk] facade over the generated Newsletter API from **Kotlin** (its
 * `java.util.function.Function` picks SAM-convert to `{ it.x }`). The `PathWalkJavaApiTest` sibling runs the
 * *identical* cases and paths from Java; Kotlin code normally prefers the extension DSL — see
 * [ProcessPathKotlinApiTest], which also holds the API-agnostic node-metadata / compensation checks (not
 * duplicated here).
 */
class PathWalkKotlinApiTest {

    @Test
    fun `happy path walks the subprocess interior via inside`() {
        val ids = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .inside(Newsletter.SubProcessConfirmation.Inner) { s ->
                PathWalk.from(s.startEventRequestReceived)
                    .then { it.activitySendConfirmationMail }
                    .then { it.activityConfirmRegistration }
                    .end { it.endEventSubscriptionConfirmed }
            }
            .then { it.gatewaySplitNotifications }
            .then { it.activitySendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .ids

        assertThat(ids).containsExactly(
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
    fun `interrupting timer boundary leaves the subprocess into the call activity and compensation end`() {
        val ids = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.timerAfter3Days }
            .then { it.callActivityAbortRegistration }
            .end { it.compensationEndEventRegistrationAborted }
            .ids

        assertThat(ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_After3Days",
            "CallActivity_AbortRegistration",
            "CompensationEndEvent_RegistrationAborted",
        )
    }

    @Test
    fun `error boundary leaves the subprocess into the signal end event`() {
        val ids = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.errorEventInvalidMail }
            .end { it.endEventRegistrationNotPossible }
            .ids

        assertThat(ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "ErrorEvent_InvalidMail",
            "EndEvent_RegistrationNotPossible",
        )
    }

    @Test
    fun `non-interrupting timer loop records repeats in ids and dedups them in distinctIds`() {
        val trail = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .then { it.timerEveryDay }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .end { it.endEventSubscriptionConfirmed }

        assertThat(trail.ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "EndEvent_SubscriptionConfirmed",
        )
        assertThat(trail.distinctIds).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "EndEvent_SubscriptionConfirmed",
        )
    }

    @Test
    fun `parallel branches union into a deduplicated set via nodesOf`() {
        val welcomeBranch = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.activitySendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .nodes
        val notifyBranch = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.activityNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .nodes

        assertThat(PathWalk.nodesOf(welcomeBranch, notifyBranch).map { it.id.value })
            .contains("Activity_SendWelcomeMail", "Activity_NotifyCommunity", "Gateway_JoinNotifications")
            .doesNotHaveDuplicates()
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch`() {
        val ids = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.activitySendWelcomeMail }
            .jumpTo(Newsletter.GatewaySplitNotifications)
            .then { it.activityNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .ids

        assertThat(ids).containsExactly(
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Activity_NotifyCommunity",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted",
        )
    }

    @Test
    fun `thenMultipleTimes records the same node repeatedly (builder mechanic, not a real flow)`() {
        // Newsletter has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        val walk = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .thenMultipleTimes(2) { it.activitySendWelcomeMail }

        assertThat(walk.ids)
            .containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail", "Activity_SendWelcomeMail")
        assertThat(walk.distinctIds).containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail")
        assertThat(walk.nodes.map { it.id.value })
            .containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail", "Activity_SendWelcomeMail")
    }
}
