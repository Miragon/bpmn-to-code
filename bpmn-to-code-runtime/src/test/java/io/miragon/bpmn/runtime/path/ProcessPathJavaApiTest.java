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
            var i1 = then(i0, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::serviceTaskSendConfirmationMail);
            var i2 = then(i1, Relations.SubProcessConfirmation.ServiceTaskSendConfirmationMail.Next::userTaskConfirmRegistration);
            return then(i2, Relations.SubProcessConfirmation.UserTaskConfirmRegistration.Next::endEventSubscriptionConfirmed);
        });
        var p4 = then(p3, Relations.SubProcessConfirmation.Next::gatewaySplitNotifications);
        var p5 = then(p4, Relations.GatewaySplitNotifications.Next::serviceTaskSendWelcomeMail);
        var p6 = then(p5, Relations.ServiceTaskSendWelcomeMail.Next::gatewayJoinNotifications);
        var p7 = then(p6, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);

        assertThat(p7.getIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "endEvent_subscriptionConfirmed",
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted"
        );
    }

    // --- Subprocess boundary events -----------------------------------------------------------------------

    @Test
    void interruptingTimerBoundaryLeavesTheSubprocessIntoTheCallActivityAndCompensationEnd() {
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = onto(p1, Relations.ServiceTaskIncrementSubscriptionCounter.Next::subProcessConfirmation);
        var p3 = enter(p2, Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p4 = then(p3, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::serviceTaskSendConfirmationMail);
        var p5 = then(p4, Relations.SubProcessConfirmation.ServiceTaskSendConfirmationMail.Next::userTaskConfirmRegistration);
        var p6 = interruptedBy(p5, Relations.subProcessConfirmation(), Relations.SubProcessConfirmation.Next::timerAfter3Days);
        var p7 = then(p6, Relations.TimerAfter3Days.Next::callActivityAbortRegistration);
        var p8 = then(p7, Relations.CallActivityAbortRegistration.Next::compensationEndEventRegistrationAborted);

        assertThat(p8.getIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_after3Days",
            "callActivity_abortRegistration",
            "compensationEndEvent_registrationAborted"
        );
    }

    @Test
    void errorBoundaryLeavesTheSubprocessIntoTheSignalEndEvent() {
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = onto(p1, Relations.ServiceTaskIncrementSubscriptionCounter.Next::subProcessConfirmation);
        var p3 = enter(p2, Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p4 = then(p3, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::serviceTaskSendConfirmationMail);
        var p5 = interruptedBy(p4, Relations.subProcessConfirmation(), Relations.SubProcessConfirmation.Next::errorEventInvalidMail);
        var p6 = then(p5, Relations.ErrorEventInvalidMail.Next::endEventRegistrationNotPossible);

        assertThat(p6.getIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "errorEvent_invalidMail",
            "endEvent_registrationNotPossible"
        );
    }

    @Test
    void nonInterruptingTimerResendLoopIsWalkedInTheInteriorEnteredViaAnExplicitScope() {
        var p0 = ProcessPath.from(Relations.startEventSubmitRegistrationForm());
        var p1 = then(p0, Relations.StartEventSubmitRegistrationForm.Next::serviceTaskIncrementSubscriptionCounter);
        var p2 = enter(p1, Relations.subProcessConfirmation().inner(), Relations.SubProcessConfirmation.Inner.Next::startEventRequestReceived);
        var p3 = then(p2, Relations.SubProcessConfirmation.StartEventRequestReceived.Next::serviceTaskSendConfirmationMail);
        var p4 = then(p3, Relations.SubProcessConfirmation.ServiceTaskSendConfirmationMail.Next::userTaskConfirmRegistration);
        var p5 = then(p4, Relations.SubProcessConfirmation.UserTaskConfirmRegistration.Next::timerEveryDay);
        var p6 = then(p5, Relations.SubProcessConfirmation.TimerEveryDay.Next::serviceTaskSendConfirmationMail);
        var p7 = then(p6, Relations.SubProcessConfirmation.ServiceTaskSendConfirmationMail.Next::userTaskConfirmRegistration);
        var p8 = then(p7, Relations.SubProcessConfirmation.UserTaskConfirmRegistration.Next::endEventSubscriptionConfirmed);

        assertThat(p8.getIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_everyDay",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "endEvent_subscriptionConfirmed"
        );
        assertThat(p8.getDistinctIds()).containsExactly(
            "startEvent_submitRegistrationForm",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_requestReceived",
            "serviceTask_sendConfirmationMail",
            "userTask_confirmRegistration",
            "timer_everyDay",
            "endEvent_subscriptionConfirmed"
        );
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    void parallelBranchesAssertAsAnUnorderedSetViaNodesOf() {
        var welcomeStart = ProcessPath.from(Relations.gatewaySplitNotifications());
        var w1 = then(welcomeStart, Relations.GatewaySplitNotifications.Next::serviceTaskSendWelcomeMail);
        var w2 = then(w1, Relations.ServiceTaskSendWelcomeMail.Next::gatewayJoinNotifications);
        var w3 = then(w2, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);
        List<FlowNode> welcomeBranch = w3.getNodes();

        var notifyStart = ProcessPath.from(Relations.gatewaySplitNotifications());
        var t1 = then(notifyStart, Relations.GatewaySplitNotifications.Next::serviceTaskNotifyCommunity);
        var t2 = then(t1, Relations.ServiceTaskNotifyCommunity.Next::gatewayJoinNotifications);
        var t3 = then(t2, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);
        List<FlowNode> notifyBranch = t3.getNodes();

        var ids = nodesOf(welcomeBranch, notifyBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        // nodesOf unions the branches and de-duplicates the shared join/end nodes by ElementId — the same as
        // Kotlin, now that AbstractFlowNode has id-based equals/hashCode (the Java accessors return fresh
        // instances, but equal-by-id ones).
        assertThat(ids)
            .contains("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity", "gateway_joinNotifications")
            .doesNotHaveDuplicates();
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranchInOneChain() {

        var p0 = ProcessPath.from(Relations.gatewaySplitNotifications());
        var p1 = then(p0, Relations.GatewaySplitNotifications.Next::serviceTaskSendWelcomeMail);
        var p2 = jumpTo(p1, Relations.gatewaySplitNotifications());
        var p3 = then(p2, Relations.GatewaySplitNotifications.Next::serviceTaskNotifyCommunity);
        var p4 = then(p3, Relations.ServiceTaskNotifyCommunity.Next::gatewayJoinNotifications);
        var p5 = then(p4, Relations.GatewayJoinNotifications.Next::endEventRegistrationCompleted);

        var ids = p5.getNodes().stream().map(n -> n.getId().getValue()).toList();
        assertThat(ids).containsExactly(
            "gateway_splitNotifications",
            "serviceTask_sendWelcomeMail",
            "serviceTask_notifyCommunity",
            "gateway_joinNotifications",
            "endEvent_registrationCompleted"
        );
    }

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    void nodesExposeTheirIdAndFlatElementTypeAcrossElementKinds() {
        assertThat(Relations.startEventSubmitRegistrationForm().getElementType()).isEqualTo("MESSAGE_START_EVENT");
        assertThat(Relations.gatewaySplitNotifications().getElementType()).isEqualTo("PARALLEL_GATEWAY");
        assertThat(Relations.callActivityAbortRegistration().getElementType()).isEqualTo("CALL_ACTIVITY");
        assertThat(Relations.serviceTaskSendWelcomeMail().getElementType()).isEqualTo("SERVICE_TASK");
        assertThat(Relations.gatewaySplitNotifications().getId().getValue()).isEqualTo("gateway_splitNotifications");
    }

    @Test
    void compensationHandlerIsReachableOnlyViaItsAccessorNotThroughTheNavigationGraph() {
        var handler = Relations.serviceTaskDecrementSubscriptionCounter();
        assertThat(handler.getId().getValue()).isEqualTo("serviceTask_decrementSubscriptionCounter");
        assertThat(handler.getElementType()).isEqualTo("SERVICE_TASK");
    }
}
