package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.NewsletterSubscriptionProcessApi.Relations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.enter;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.inside;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.interruptedBy;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.jumpTo;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.nodesOf;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.onto;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.then;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code ProcessPathKotlinApiTest} over the generated *Java* Newsletter API. Because there is no fluent
 * chaining from Java, each step is a static call on {@code ProcessPathStepsKt} with the intermediate path held
 * in a local ({@code var}). This doubles as the compile contract that the generated Java navigation code
 * ({@code extends AbstractFlowNode implements HasSuccessors<Next> / HasInnerScope}) resolves against the runtime
 * interfaces when consumed from Java.
 */
class ProcessPathJavaApiTest {

    // --- Sequential flow through a subprocess -------------------------------------------------------------

    @Test
    void happyPathWalksTheSubprocessInteriorWithInsideAndContinuesCheckedAfterIt() {

        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = onto(p1, Relations.ServiceTaskIncrementSubscriptionCounter.Next::subProcessConfirmation);
        var p3 = inside(p2, sub -> {
            var i0 = enter(sub, Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
            var i1 = then(i0, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::activitySendConfirmationMail);
            var i2 = then(i1, Relations.SubProcessConfirmation.ActivitySendConfirmationMail.Next::activityConfirmRegistration);
            return then(i2, Relations.SubProcessConfirmation.ActivityConfirmRegistration.Next::endEventSubscriptionConfirmed);
        });
        var p4 = then(p3, Relations.SubProcessConfirmation.Next::gatewaySplitNotifications);
        var p5 = then(p4, Relations.GatewaySplitNotifications.Next::activitySendWelcomeMail);
        var p6 = then(p5, Relations.ActivitySendWelcomeMail.Next::gatewayJoinNotifications);
        var p7 = then(p6, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);

        assertThat(p7.getIds()).containsExactly(
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

    // --- Subprocess boundary events -----------------------------------------------------------------------

    @Test
    void interruptingTimerBoundaryLeavesTheSubprocessIntoTheCallActivityAndCompensationEnd() {
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = onto(p1, Relations.ServiceTaskIncrementSubscriptionCounter.Next::subProcessConfirmation);
        var p3 = enter(p2, Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p4 = then(p3, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::activitySendConfirmationMail);
        var p5 = then(p4, Relations.SubProcessConfirmation.ActivitySendConfirmationMail.Next::activityConfirmRegistration);
        var p6 = interruptedBy(p5, Relations.subProcessConfirmation(), Relations.SubProcessConfirmation.Next::timerAfter3Days);
        var p7 = then(p6, Relations.TimerAfter3Days.Next::callActivityAbortRegistration);
        var p8 = then(p7, Relations.CallActivityAbortRegistration.Next::compensationEndEventRegistrationAborted);

        assertThat(p8.getIds()).containsExactly(
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
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = onto(p1, Relations.ServiceTaskIncrementSubscriptionCounter.Next::subProcessConfirmation);
        var p3 = enter(p2, Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p4 = then(p3, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::activitySendConfirmationMail);
        var p5 = interruptedBy(p4, Relations.subProcessConfirmation(), Relations.SubProcessConfirmation.Next::errorEventInvalidMail);
        var p6 = then(p5, Relations.ErrorEventInvalidMail.Next::endEventRegistrationNotPossible);

        assertThat(p6.getIds()).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "ErrorEvent_InvalidMail",
            "EndEvent_RegistrationNotPossible"
        );
    }

    @Test
    void nonInterruptingTimerResendLoopIsWalkedInTheInteriorEnteredViaAnExplicitScope() {
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = enter(p1, Relations.subProcessConfirmation().inner(), Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p3 = then(p2, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::activitySendConfirmationMail);
        var p4 = then(p3, Relations.SubProcessConfirmation.ActivitySendConfirmationMail.Next::activityConfirmRegistration);
        var p5 = then(p4, Relations.SubProcessConfirmation.ActivityConfirmRegistration.Next::timerEveryDay);
        var p6 = then(p5, Relations.SubProcessConfirmation.TimerEveryDay.Next::activitySendConfirmationMail);
        var p7 = then(p6, Relations.SubProcessConfirmation.ActivitySendConfirmationMail.Next::activityConfirmRegistration);
        var p8 = then(p7, Relations.SubProcessConfirmation.ActivityConfirmRegistration.Next::endEventSubscriptionConfirmed);

        assertThat(p8.getIds()).containsExactly(
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
        assertThat(p8.getDistinctIds()).containsExactly(
            "StartEvent_SubmitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_RequestReceived",
            "Activity_SendConfirmationMail",
            "Activity_ConfirmRegistration",
            "Timer_EveryDay",
            "EndEvent_SubscriptionConfirmed"
        );
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    void parallelBranchesAssertAsAnUnorderedSetViaNodesOf() {
        var welcomeStart = ProcessPath.from(Relations.gatewaySplitNotifications());
        var w1 = then(welcomeStart, Relations.GatewaySplitNotifications.Next::activitySendWelcomeMail);
        var w2 = then(w1, Relations.ActivitySendWelcomeMail.Next::gatewayJoinNotifications);
        var w3 = then(w2, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);
        List<FlowNode> welcomeBranch = w3.getNodes();

        var notifyStart = ProcessPath.from(Relations.gatewaySplitNotifications());
        var t1 = then(notifyStart, Relations.GatewaySplitNotifications.Next::activityNotifyCommunity);
        var t2 = then(t1, Relations.ActivityNotifyCommunity.Next::gatewayJoinNotifications);
        var t3 = then(t2, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);
        List<FlowNode> notifyBranch = t3.getNodes();

        var ids = nodesOf(welcomeBranch, notifyBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        // nodesOf unions the branches and de-duplicates the shared join/end nodes by ElementId — the same as
        // Kotlin, now that AbstractFlowNode has id-based equals/hashCode (the Java accessors return fresh
        // instances, but equal-by-id ones).
        assertThat(ids)
            .contains("Activity_SendWelcomeMail", "Activity_NotifyCommunity", "Gateway_JoinNotifications")
            .doesNotHaveDuplicates();
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranchInOneChain() {

        var p0 = ProcessPath.from(Relations.gatewaySplitNotifications());
        var p1 = then(p0, Relations.GatewaySplitNotifications.Next::activitySendWelcomeMail);
        var p2 = jumpTo(p1, Relations.gatewaySplitNotifications());
        var p3 = then(p2, Relations.GatewaySplitNotifications.Next::activityNotifyCommunity);
        var p4 = then(p3, Relations.ActivityNotifyCommunity.Next::gatewayJoinNotifications);
        var p5 = then(p4, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);

        var ids = p5.getNodes().stream().map(n -> n.getId().getValue()).toList();
        assertThat(ids).containsExactly(
            "Gateway_SplitNotifications",
            "Activity_SendWelcomeMail",
            "Activity_NotifyCommunity",
            "Gateway_JoinNotifications",
            "EndEvent_RegistrationCompleted"
        );
    }

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    void nodesExposeTheirIdAndFlatElementTypeAcrossElementKinds() {
        assertThat(Relations.startEventSubmitRegistrationForm().getElementType()).isEqualTo("MESSAGE_START_EVENT");
        assertThat(Relations.gatewaySplitNotifications().getElementType()).isEqualTo("PARALLEL_GATEWAY");
        assertThat(Relations.callActivityAbortRegistration().getElementType()).isEqualTo("CALL_ACTIVITY");
        assertThat(Relations.activitySendWelcomeMail().getElementType()).isEqualTo("SERVICE_TASK");
        assertThat(Relations.gatewaySplitNotifications().getId().getValue()).isEqualTo("Gateway_SplitNotifications");
    }

    @Test
    void compensationHandlerIsReachableOnlyViaItsAccessorNotThroughTheNavigationGraph() {
        var handler = Relations.compensationTaskDecrementSubscriptionCounter();
        assertThat(handler.getId().getValue()).isEqualTo("CompensationTask_DecrementSubscriptionCounter");
        assertThat(handler.getElementType()).isEqualTo("SERVICE_TASK");
    }
}
