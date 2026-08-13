package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.NewsletterSubscriptionProcessApi.Relations;
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
        var ids = PathWalk.from(Relations.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .onto(n -> n.subProcessConfirmation())
            .inside(Relations.subProcessConfirmation().inner(), s ->
                PathWalk.from(s.startEventRequestReceived())
                    .then(n -> n.activitySendConfirmationMail())
                    .then(n -> n.activityConfirmRegistration())
                    .end(n -> n.endEventSubscriptionConfirmed()))
            .then(n -> n.gatewaySplitNotifications())
            .then(n -> n.activitySendWelcomeMail())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getIds();

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
            "EndEvent_RegistrationCompleted"
        );
    }

    @Test
    void interruptingTimerBoundaryLeavesTheSubprocessIntoTheCallActivityAndCompensationEnd() {
        var ids = PathWalk.from(Relations.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Relations.subProcessConfirmation().inner(), s -> s.startEventRequestReceived())
            .then(n -> n.activitySendConfirmationMail())
            .then(n -> n.activityConfirmRegistration())
            .interruptedBy(Relations.subProcessConfirmation(), n -> n.timerAfter3Days())
            .then(n -> n.callActivityAbortRegistration())
            .end(n -> n.compensationEndEventRegistrationAborted())
            .getIds();

        assertThat(ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_After3Days",
            "CallActivity_AbortRegistration",
            "CompensationEndEvent_RegistrationAborted"
        );
    }

    @Test
    void errorBoundaryLeavesTheSubprocessIntoTheSignalEndEvent() {
        var ids = PathWalk.from(Relations.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Relations.subProcessConfirmation().inner(), s -> s.startEventRequestReceived())
            .then(n -> n.activitySendConfirmationMail())
            .interruptedBy(Relations.subProcessConfirmation(), n -> n.errorEventInvalidMail())
            .end(n -> n.endEventRegistrationNotPossible())
            .getIds();

        assertThat(ids).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "ErrorEvent_InvalidMail",
            "EndEvent_RegistrationNotPossible"
        );
    }

    @Test
    void nonInterruptingTimerLoopRecordsRepeatsInIdsAndDedupsThemInDistinctIds() {
        var trail = PathWalk.from(Relations.startEventSubmitRegistrationForm())
            .then(n -> n.serviceTaskIncrementSubscriptionCounter())
            .enter(Relations.subProcessConfirmation().inner(), s -> s.startEventRequestReceived())
            .then(n -> n.activitySendConfirmationMail())
            .then(n -> n.activityConfirmRegistration())
            .then(n -> n.timerEveryDay())
            .then(n -> n.activitySendConfirmationMail())
            .then(n -> n.activityConfirmRegistration())
            .end(n -> n.endEventSubscriptionConfirmed());

        assertThat(trail.getIds()).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "EndEvent_SubscriptionConfirmed"
        );
        assertThat(trail.getDistinctIds()).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "EndEvent_SubscriptionConfirmed"
        );
    }

    @Test
    void parallelBranchesUnionIntoADeduplicatedSetViaNodesOf() {
        List<FlowNode> welcomeBranch = PathWalk.from(Relations.gatewaySplitNotifications())
            .then(n -> n.activitySendWelcomeMail())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getNodes();
        List<FlowNode> notifyBranch = PathWalk.from(Relations.gatewaySplitNotifications())
            .then(n -> n.activityNotifyCommunity())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getNodes();

        var ids = PathWalk.nodesOf(welcomeBranch, notifyBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        assertThat(ids)
            .contains("Activity_SendWelcomeMail", "Activity_NotifyCommunity", "Gateway_JoinNotifications")
            .doesNotHaveDuplicates();
    }

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranch() {
        // RiskyNavigation is not enforced for Java callers (no @OptIn equivalent) — the intent is documented.
        var ids = PathWalk.from(Relations.gatewaySplitNotifications())
            .then(n -> n.activitySendWelcomeMail())
            .jumpTo(Relations.gatewaySplitNotifications())
            .then(n -> n.activityNotifyCommunity())
            .then(n -> n.gatewayJoinNotifications())
            .end(n -> n.endEventRegistrationCompleted())
            .getIds();

        assertThat(ids).containsExactly(
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Activity_NotifyCommunity",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted"
        );
    }

    @Test
    void thenMultipleTimesRecordsTheSameNodeRepeatedly() {
        // Newsletter has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        var walk = PathWalk.from(Relations.gatewaySplitNotifications())
            .thenMultipleTimes(2, n -> n.activitySendWelcomeMail());

        assertThat(walk.getIds())
            .containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail", "Activity_SendWelcomeMail");
        assertThat(walk.getDistinctIds())
            .containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail");
        assertThat(walk.getNodes().stream().map(n -> n.getId().getValue()).toList())
            .containsExactly("Gateway_SplitNotifications", "Activity_SendWelcomeMail", "Activity_SendWelcomeMail");
    }
}
