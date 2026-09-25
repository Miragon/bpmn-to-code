package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.NewsletterSubscriptionProcessApi.Flow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the fluent {@link PathWalk} facade over the generated Java Newsletter API from <em>Java</em> — the
 * recommended fluent form for Java consumers. Runs the <em>identical</em> cases and paths as
 * {@code PathWalkKotlinApiTest}. The API-agnostic node-metadata / compensation checks live once in the
 * extension-DSL tests ({@code ProcessPathKotlinApiTest} / {@code ProcessPathJavaApiTest}) and are not
 * duplicated here. The compile-time edge check is intact: {@code n} is the current node's {@code Next}.
 */
class PathWalkJavaApiTest {

    @Test
    void happyPathWalksTheSubprocessInteriorViaInside() {
        var ids = PathWalk.from(Flow.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .onto(n -> n.subProcessConfirmation())
            .inside(Flow.subProcessConfirmation(), s ->
                PathWalk.from(s.startEventRequestReceived())
                    .then(n -> n.serviceTaskSendConfirmationMail())
                    .then(n -> n.receiveTaskConfirmRegistration())
                    .end(n -> n.endEventSubscriptionConfirmed()))
            .then(n -> n.gatewaySplitNotifications())
            .then(n -> n.serviceTaskSendWelcomeMail())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getIds();

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "receiveTask_confirmRegistration",
            "endEvent_subscriptionConfirmed",
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted"
        );
    }

    @Test
    void interruptingTimerBoundaryLeavesTheSubprocessIntoTheCallActivityAndCompensationEnd() {
        var ids = PathWalk.from(Flow.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Flow.subProcessConfirmation(), s -> s.startEventRequestReceived())
            .then(n -> n.serviceTaskSendConfirmationMail())
            .then(n -> n.receiveTaskConfirmRegistration())
            .interruptedBy(Flow.subProcessConfirmation(), n -> n.timerAfter3Days())
            .then(n -> n.callActivityAbortRegistration())
            .end(n -> n.compensationEndEventRegistrationAborted())
            .getIds();

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "receiveTask_confirmRegistration",
            "timer_after3Days",
            "callActivity_abortRegistration",
            "compensationEndEvent_registrationAborted"
        );
    }

    @Test
    void errorBoundaryLeavesTheSubprocessIntoTheSignalEndEvent() {
        var ids = PathWalk.from(Flow.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Flow.subProcessConfirmation(), s -> s.startEventRequestReceived())
            .then(n -> n.serviceTaskSendConfirmationMail())
            .interruptedBy(Flow.subProcessConfirmation(), n -> n.errorEventInvalidMail())
            .end(n -> n.endEventRegistrationNotPossible())
            .getIds();

        assertThat(ids).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "errorEvent_invalidMail",
            "endEvent_registrationNotPossible"
        );
    }

    @Test
    void nonInterruptingTimerLoopRecordsRepeatsInIdsAndDedupsThemInDistinctIds() {
        var trail = PathWalk.from(Flow.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Flow.subProcessConfirmation(), s -> s.startEventRequestReceived())
            .then(n -> n.serviceTaskSendConfirmationMail())
            .then(n -> n.receiveTaskConfirmRegistration())
            .then(n -> n.timerEveryDay())
            .then(n -> n.serviceTaskSendConfirmationMail())
            .then(n -> n.receiveTaskConfirmRegistration())
            .end(n -> n.endEventSubscriptionConfirmed());

        assertThat(trail.getIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "receiveTask_confirmRegistration",
            "timer_everyDay",
            "serviceTask_sendConfirmationMail",
            "receiveTask_confirmRegistration",
            "endEvent_subscriptionConfirmed"
        );
        assertThat(trail.getDistinctIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "receiveTask_confirmRegistration",
            "timer_everyDay",
            "endEvent_subscriptionConfirmed"
        );
    }

    @Test
    void parallelBranchesUnionIntoADeduplicatedSetViaNodesOf() {
        List<FlowNode> welcomeBranch = PathWalk.from(Flow.gatewaySplitNotifications())
            .then(n -> n.serviceTaskSendWelcomeMail())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getNodes();
        List<FlowNode> notifyBranch = PathWalk.from(Flow.gatewaySplitNotifications())
            .then(n -> n.serviceTaskNotifyCommunity())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getNodes();

        var ids = PathWalk.nodesOf(welcomeBranch, notifyBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        assertThat(ids)
            .contains("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity", "gateway_joinNotifications")
            .doesNotHaveDuplicates();
    }

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranch() {
        // RiskyNavigation is not enforced for Java callers (no @OptIn equivalent) — the intent is documented.
        var ids = PathWalk.from(Flow.gatewaySplitNotifications())
            .then(n -> n.serviceTaskSendWelcomeMail())
            .jumpTo(Flow.gatewaySplitNotifications())
            .then(n -> n.serviceTaskNotifyCommunity())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getIds();

        assertThat(ids).containsExactly(
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "serviceTask_notifyCommunity",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted"
        );
    }

    @Test
    void thenMultipleTimesRecordsTheSameNodeRepeatedly() {
        // Newsletter has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        var walk = PathWalk.from(Flow.gatewaySplitNotifications())
            .thenMultipleTimes(2, n -> n.serviceTaskSendWelcomeMail());

        assertThat(walk.getIds())
            .containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail", "serviceTask_sendWelcomeMail");
        assertThat(walk.getDistinctIds())
            .containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail");
        assertThat(walk.getNodes().stream().map(n -> n.getId().getValue()).toList())
            .containsExactly("gateway_splitNotifications", "serviceTask_sendWelcomeMail", "serviceTask_sendWelcomeMail");
    }
}
