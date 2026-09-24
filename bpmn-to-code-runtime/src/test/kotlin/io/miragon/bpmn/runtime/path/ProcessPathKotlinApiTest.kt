package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.BpmnError
import io.miragon.bpmn.runtime.BpmnTimer
import io.miragon.bpmn.runtime.MessageName
import io.miragon.bpmn.runtime.ProcessId
import io.miragon.bpmn.runtime.VariableName
import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Flow.SubProcessConfirmation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.miragon.bpmn.runtime.path.example.NewsletterSubscriptionProcessApi.Flow as Newsletter

/**
 * Exercises [ProcessPath] over the *actually generated* Kotlin Newsletter API — which doubles as the compile
 * contract that the generated `: HasSuccessors<Next>` / `: FlowNode` code resolves against the runtime
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
                .then { it.serviceTaskSendConfirmationMail }
                .then { it.userTaskConfirmRegistration }
                .then { it.endEventSubscriptionConfirmed }
        }

        val path = ProcessPath.from(Newsletter.StartEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .inside(confirmationInterior)
            .then { it.gatewaySplitNotifications }
            .then { it.serviceTaskSendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }

        assertThat(path.ids).containsExactly(
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

    // --- Subprocess boundary events -----------------------------------------------------------------------

    @Test
    fun `interrupting timer boundary leaves the subprocess into the call activity and compensation end`() {
        val path = ProcessPath.from(Newsletter.StartEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.timerAfter3Days }
            .then { it.callActivityAbortRegistration }
            .then { it.compensationEndEventRegistrationAborted }

        assertThat(path.ids).containsExactly(
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
        val path = ProcessPath.from(Newsletter.StartEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .onto { it.subProcessConfirmation }
            .enter { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .interruptedBy(Newsletter.SubProcessConfirmation) { it.errorEventInvalidMail }
            .then { it.endEventRegistrationNotPossible }

        assertThat(path.ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "errorEvent_invalidMail",
            "endEvent_registrationNotPossible",
        )
    }

    @Test
    fun `non-interrupting timer resend loop is walked in the interior, entered via an explicit scope`() {
        // enter(scope) descends straight into a named interior from a non-adjacent position (here after the
        // increment task) — the re-anchor form. The daily reminder timer is non-interrupting, so it is a normal
        // interior hop that loops back to the confirmation mail (a multi-node cycle, written out explicitly).
        val path = ProcessPath.from(Newsletter.StartEventSubmitRegistrationForm)
            .then { it.serviceTaskIncrementSubscriptionCounter }
            .enter(Newsletter.SubProcessConfirmation) { it.startEventRequestReceived }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .then { it.timerEveryDay }
            .then { it.serviceTaskSendConfirmationMail }
            .then { it.userTaskConfirmRegistration }
            .then { it.endEventSubscriptionConfirmed }

        assertThat(path.ids).containsExactly(
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
        assertThat(path.distinctIds).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_everyDay",
            "endEvent_subscriptionConfirmed",
        )
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    fun `parallel branches assert as an unordered deduplicated set via nodesOf`() {
        val welcomeBranch = ProcessPath.from(Newsletter.GatewaySplitNotifications)
            .then { it.serviceTaskSendWelcomeMail }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes
        val notifyBranch = ProcessPath.from(Newsletter.GatewaySplitNotifications)
            .then { it.serviceTaskNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes

        assertThat(nodesOf(welcomeBranch, notifyBranch).map { it.id.value })
            .contains("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity", "gateway_joinNotifications")
            .doesNotHaveDuplicates()
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch in one chain`() {
        val passed = ProcessPath.from(Newsletter.GatewaySplitNotifications)
            .then { it.serviceTaskSendWelcomeMail }
            .jumpTo(Newsletter.GatewaySplitNotifications)
            .then { it.serviceTaskNotifyCommunity }
            .then { it.gatewayJoinNotifications }
            .then { it.endEventRegistrationCompleted }
            .nodes

        assertThat(passed.map { it.id.value }).containsExactly(
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "serviceTask_notifyCommunity",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted",
        )
    }

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    fun `nodes expose their id and flat elementType across element kinds`() {
        assertThat(Newsletter.StartEventSubmitRegistrationForm.elementType).isEqualTo("MESSAGE_START_EVENT")
        assertThat(Newsletter.GatewaySplitNotifications.elementType).isEqualTo("PARALLEL_GATEWAY")
        assertThat(Newsletter.CallActivityAbortRegistration.elementType).isEqualTo("CALL_ACTIVITY")
        assertThat(Newsletter.ServiceTaskSendWelcomeMail.elementType).isEqualTo("SERVICE_TASK")
        assertThat(Newsletter.GatewaySplitNotifications.id.value).isEqualTo("gateway_splitNotifications")
    }

    @Test
    fun `nodes expose their display name, typed sequence-flow edges and their own facets`() {
        assertThat(Newsletter.UserTaskConfirmRegistration.name).isEqualTo("Confirm registration")
        assertThat(Newsletter.StartEventSubmitRegistrationForm.name).isNull()

        val edge = Newsletter.StartEventSubmitRegistrationForm.flows().flowSubmitToIncrementCounter
        assertThat(edge.target).isEqualTo(Newsletter.ServiceTaskIncrementSubscriptionCounter)
        assertThat(edge.conditionExpression).isNull()
        assertThat(edge.isDefault).isFalse()
        assertThat(edge).isEqualTo(Newsletter.StartEventSubmitRegistrationForm.flows().flowSubmitToIncrementCounter)

        val input: VariableName.Input = Newsletter.ServiceTaskSendConfirmationMail.Variables.SUBSCRIPTION_ID
        assertThat(input.value).isEqualTo("subscriptionId")
        assertThat(Newsletter.ServiceTaskSendWelcomeMail.JOB_TYPE).isEqualTo("\${newsletterSendWelcomeMail}")
        assertThat(Newsletter.StartEventSubmitRegistrationForm.message).isEqualTo(MessageName("Message_FormSubmitted"))
        assertThat(Newsletter.ErrorEventInvalidMail.error).isEqualTo(BpmnError("Error_InvalidMail", "500"))

        assertThat(Newsletter.TimerEveryDay.timer).isEqualTo(BpmnTimer("Duration", "PT1M"))
        assertThat(Newsletter.TimerEveryDay.attachedTo).isEqualTo(Newsletter.UserTaskConfirmRegistration)
        assertThat(Newsletter.TimerEveryDay.isInterrupting).isFalse()

        assertThat(Newsletter.CallActivityAbortRegistration.calledProcess).isEqualTo(ProcessId("abort-registration"))
        assertThat(Newsletter.CallActivityAbortRegistration.Inputs.CHILD_SUBSCRIPTION_ID.target).isEqualTo("childSubscriptionId")
        assertThat(Newsletter.CallActivityAbortRegistration.Outputs.ABORT_RESULT.source).isEqualTo("childAbortResult")
    }

    @Test
    fun `compensation handler is reachable only by name, not through the navigation graph`() {
        // Compensation handlers hang off a boundary event via an association, not a sequence flow, so they have
        // no incoming edge in the graph — no then/onto/enter reaches them. They stay addressable by name.
        val handler = Newsletter.ServiceTaskDecrementSubscriptionCounter
        assertThat(handler.id.value).isEqualTo("serviceTask_decrementSubscriptionCounter")
        assertThat(handler.elementType).isEqualTo("SERVICE_TASK")
    }
}
