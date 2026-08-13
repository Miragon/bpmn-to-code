package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Relations as Newsletter
import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Relations.SubProcessConfirmation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Exercises [ProcessPath] over the *actually generated* Kotlin Newsletter API — which doubles as the compile
 * contract that the generated `: HasSuccessors<Next>` / `: FlowNode` / `HasInnerScope` code resolves against the runtime
 * interfaces. Newsletter is the single navigation fixture and covers every element type: message/plain start,
 * service/receive task, embedded subprocess with interior, parallel (AND) split/join, call activity, boundary
 * events (interrupting timer, error, non-interrupting timer), signal end, and compensation.
 *
 * The `ProcessPathJavaApiTest` sibling mirrors these cases over the generated Java API; the [ProcessPathTest]
 * unit test covers each operator's mechanics in isolation over a hand-built stub graph.
 */
class ProcessPathKotlinApiTest {

    // --- Sequential flow through a subprocess -------------------------------------------------------------

    @Test
    fun `happy path walks the subprocess interior with inside and continues checked after it`() {
        // The interior walk as a reusable, fully-checked block — typed on the subprocess so every hop compiles.
        val confirmationInterior: ProcessPath<SubProcessConfirmation>.() -> ProcessPath<*> = {
            enter { it.startEventRequestReceived }
                .then { it.activitySendConfirmationMail }
                .then { it.activityConfirmRegistration }
                .then { it.endEventSubscriptionConfirmed }
        }

        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .inside(confirmationInterior)
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

    // --- Subprocess boundary events -----------------------------------------------------------------------

    @Test
    fun `interrupting timer boundary leaves the subprocess into the call activity and compensation end`() {
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.timerAfter3Days }
            .then { it.callActivityAbortRegistration }
            .then { it.compensationEndEventRegistrationAborted }

        assertThat(path.ids).containsExactly(
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
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.errorEventInvalidMail }
            .then { it.endEventRegistrationNotPossible }

        assertThat(path.ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "ErrorEvent_InvalidMail",
            "EndEvent_RegistrationNotPossible",
        )
    }

    @Test
    fun `non-interrupting timer resend loop is walked in the interior, entered via an explicit scope`() {
        // enter(scope) descends straight into a named interior from a non-adjacent position (here after the
        // increment task) — the re-anchor form. The daily reminder timer is non-interrupting, so it is a normal
        // interior hop that loops back to the confirmation mail (a multi-node cycle, written out explicitly).
        val path = ProcessPath.from(Newsletter.startEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation.Inner) { it.startEventRequestReceived }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .then { it.timerEveryDay }
            .then { it.activitySendConfirmationMail }
            .then { it.activityConfirmRegistration }
            .then { it.endEventSubscriptionConfirmed }

        assertThat(path.ids).containsExactly(
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
        assertThat(path.distinctIds).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "EndEvent_SubscriptionConfirmed",
        )
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    fun `parallel branches assert as an unordered deduplicated set via nodesOf`() {
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

        assertThat(nodesOf(welcomeBranch, notifyBranch).map { it.id.value })
            .contains("Activity_SendWelcomeMail", "Activity_NotifyCommunity", "Gateway_JoinNotifications")
            .doesNotHaveDuplicates()
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch in one chain`() {
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

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    fun `nodes expose their id and flat elementType across element kinds`() {
        assertThat(Newsletter.startEventSubmitRegistrationForm.elementType).isEqualTo("MESSAGE_START_EVENT")
        assertThat(Newsletter.gatewaySplitNotifications.elementType).isEqualTo("PARALLEL_GATEWAY")
        assertThat(Newsletter.callActivityAbortRegistration.elementType).isEqualTo("CALL_ACTIVITY")
        assertThat(Newsletter.activitySendWelcomeMail.elementType).isEqualTo("SERVICE_TASK")
        assertThat(Newsletter.gatewaySplitNotifications.id.value).isEqualTo("Gateway_SplitNotifications")
    }

    @Test
    fun `compensation handler is reachable only via its accessor, not through the navigation graph`() {
        // Compensation handlers hang off a boundary event via an association, not a sequence flow, so they have
        // no incoming edge in the graph — no then/onto/enter reaches them. They stay addressable by name.
        val handler = Newsletter.compensationTaskDecrementSubscriptionCounter
        assertThat(handler.id.value).isEqualTo("CompensationTask_DecrementSubscriptionCounter")
        assertThat(handler.elementType).isEqualTo("SERVICE_TASK")
    }
}
