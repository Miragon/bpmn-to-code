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
                    .then { it.serviceTaskSendConfirmationMail }
                    .then { it.userTaskConfirmRegistration }
                    .end { it.endEventSubscriptionConfirmed }
            }
            .then { it.gatewaySplitNotifications }
            .then { it.serviceTaskSendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .ids

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "endEvent_subscriptionConfirmed",
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted",
        )
    }

    @Test
    fun `interrupting timer boundary leaves the subprocess into the call activity and compensation end`() {
        val ids = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.timerAfter3Days }
            .then { it.callActivityAbortRegistration }
            .end { it.compensationEndEventRegistrationAborted }
            .ids

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_after3Days",
            "callActivity_abortRegistration",
            "compensationEndEvent_registrationAborted",
        )
    }

    @Test
    fun `error boundary leaves the subprocess into the signal end event`() {
        val ids = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.errorEventInvalidMail }
            .end { it.endEventRegistrationNotPossible }
            .ids

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "errorEvent_invalidMail",
            "endEvent_registrationNotPossible",
        )
    }

    @Test
    fun `non-interrupting timer loop records repeats in ids and dedups them in distinctIds`() {
        val trail = PathWalk.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .then { it.timerEveryDay }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .end { it.endEventSubscriptionConfirmed }

        assertThat(trail.ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_everyDay",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "endEvent_subscriptionConfirmed",
        )
        assertThat(trail.distinctIds).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_everyDay",
            "endEvent_subscriptionConfirmed",
        )
    }

    @Test
    fun `parallel branches union into a deduplicated set via nodesOf`() {
        val welcomeBranch = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.serviceTaskSendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .nodes
        val notifyBranch = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.serviceTaskNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .nodes

        assertThat(PathWalk.nodesOf(welcomeBranch, notifyBranch).map { it.id.value })
            .contains("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity", "gateway_joinNotifications")
            .doesNotHaveDuplicates()
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch`() {
        val ids = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .then { it.serviceTaskSendWelcomeMail }
            .jumpTo(Newsletter.GatewaySplitNotifications)
            .then { it.serviceTaskNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .end { it.endEventRegistrationCompleted }
            .ids

        assertThat(ids).containsExactly(
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "serviceTask_notifyCommunity",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted",
        )
    }

    @Test
    fun `thenMultipleTimes records the same node repeatedly (builder mechanic, not a real flow)`() {
        // Newsletter has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        val walk = PathWalk.from(Newsletter.gatewaySplitNotifications)
            .thenMultipleTimes(2) { it.serviceTaskSendWelcomeMail }

        assertThat(walk.ids)
            .containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail", "serviceTask_sendWelcomeMail")
        assertThat(walk.distinctIds).containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail")
        assertThat(walk.nodes.map { it.id.value })
            .containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail", "serviceTask_sendWelcomeMail")
    }
}
