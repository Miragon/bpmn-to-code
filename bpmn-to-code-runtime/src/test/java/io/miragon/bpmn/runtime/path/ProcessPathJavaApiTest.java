package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.BoundaryEvent;
import io.miragon.bpmn.runtime.BpmnElementType;
import io.miragon.bpmn.runtime.BpmnEventType;
import io.miragon.bpmn.runtime.BpmnTimer;
import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.MessageName;
import io.miragon.bpmn.runtime.ProcessId;
import io.miragon.bpmn.runtime.TimerType;
import io.miragon.bpmn.runtime.VariableName;
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi;
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.Flow;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
 * Mirrors {@code ProcessPathKotlinApiTest} over the generated *Java* bike-leasing API. Because there is no fluent
 * chaining from Java, each step is a static call on {@code ProcessPathStepsKt} with the intermediate path held
 * in a local ({@code var}). This doubles as the compile contract that the generated Java navigation code
 * ({@code extends AbstractFlowNode implements HasSuccessors<Next> / FlowScope}) resolves against the runtime
 * interfaces when consumed from Java.
 */
class ProcessPathJavaApiTest {

    // --- Sequential flow through a subprocess -------------------------------------------------------------

    @Test
    void happyPathWalksTheSubprocessInteriorWithInsideAndContinuesCheckedAfterIt() {

        var p0 = ProcessPath.from(Flow.startEventLeasingRequestReceived());
        var p1 = then(p0, Flow.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, Flow.ServiceTaskValidateApplication.Next::businessRuleTaskCheckCreditRating);
        var p3 = then(p2, Flow.BusinessRuleTaskCheckCreditRating.Next::gatewayIsSolvent);
        var p4 = onto(p3, Flow.GatewayIsSolvent.Next::subProcessConcludeContract);
        var p5 = inside(p4, sub -> {
            var i0 = enter(sub, Flow.SubProcessConcludeContract.Start::startEventCustomerEligible);
            var i1 = then(i0, Flow.StartEventCustomerEligible.Next::serviceTaskSendContract);
            var i2 = then(i1, Flow.ServiceTaskSendContract.Next::gatewayAwaitSignature);
            var i3 = then(i2, Flow.GatewayAwaitSignature.Next::eventContractSigned);
            return then(i3, Flow.EventContractSigned.Next::endEventContractConcluded);
        });
        var p6 = then(p5, Flow.SubProcessConcludeContract.Next::gatewayFork);
        var p7 = then(p6, Flow.GatewayFork.Next::serviceTaskOrderBike);
        var p8 = then(p7, Flow.ServiceTaskOrderBike.Next::gatewayJoin);
        var p9 = then(p8, Flow.GatewayJoin.Next::receiveTaskHandoverReported);
        var p10 = then(p9, Flow.ReceiveTaskHandoverReported.Next::timerWithdrawalPeriodElapsed);
        var p11 = then(p10, Flow.TimerWithdrawalPeriodElapsed.Next::endEventLeasingActive);

        assertThat(p11.getIds()).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "event_contractSigned",
            "endEvent_contractConcluded",
            "gateway_fork",
            "serviceTask_orderBike",
            "gateway_join",
            "receiveTask_handoverReported",
            "timer_withdrawalPeriodElapsed",
            "endEvent_leasingActive"
        );
    }

    // --- Boundary events ----------------------------------------------------------------------------------

    @Test
    void escalationBoundaryLeavesTheSubprocessEnteredViaAnExplicitScopeIntoTheTerminateEnd() {
        var p0 = ProcessPath.from(Flow.startEventLeasingRequestReceived());
        var p1 = then(p0, Flow.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, Flow.ServiceTaskValidateApplication.Next::businessRuleTaskCheckCreditRating);
        var p3 = enter(p2, Flow.subProcessConcludeContract(), Flow.SubProcessConcludeContract.Start::startEventCustomerEligible);
        var p4 = then(p3, Flow.StartEventCustomerEligible.Next::serviceTaskSendContract);
        var p5 = then(p4, Flow.ServiceTaskSendContract.Next::gatewayAwaitSignature);
        var p6 = then(p5, Flow.GatewayAwaitSignature.Next::timerSignatureDeadline);
        var p7 = then(p6, Flow.TimerSignatureDeadline.Next::endEventContractNotSigned);
        var p8 = interruptedBy(p7, Flow.subProcessConcludeContract(), Flow.SubProcessConcludeContract.Next::boundaryContractNotSigned);
        var p9 = then(p8, Flow.BoundaryContractNotSigned.Next::gatewayCollectRejections);
        var p10 = then(p9, Flow.GatewayCollectRejections.Next::serviceTaskSendRejection);
        var p11 = then(p10, Flow.ServiceTaskSendRejection.Next::endEventApplicationRejected);

        assertThat(p11.getIds()).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "businessRuleTask_checkCreditRating",
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "timer_signatureDeadline",
            "endEvent_contractNotSigned",
            "boundary_contractNotSigned",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected"
        );
    }

    @Test
    void errorBoundaryOnATaskIsASuccessorOfTheTask() {
        var p0 = ProcessPath.from(Flow.startEventLeasingRequestReceived());
        var p1 = then(p0, Flow.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, Flow.ServiceTaskValidateApplication.Next::boundaryApplicationInvalid);
        var p3 = then(p2, Flow.BoundaryApplicationInvalid.Next::gatewayCollectRejections);
        var p4 = then(p3, Flow.GatewayCollectRejections.Next::serviceTaskSendRejection);
        var p5 = then(p4, Flow.ServiceTaskSendRejection.Next::endEventApplicationRejected);

        assertThat(p5.getIds()).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "boundary_applicationInvalid",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected"
        );
    }

    @Test
    void nonInterruptingTimerBoundaryBranchesOffTheSubprocessIntoTheReminder() {
        var p0 = ProcessPath.from(Flow.gatewayIsSolvent());
        var p1 = onto(p0, Flow.GatewayIsSolvent.Next::subProcessConcludeContract);
        var p2 = then(p1, Flow.SubProcessConcludeContract.Next::timerSignatureReminder);
        var p3 = then(p2, Flow.TimerSignatureReminder.Next::serviceTaskSendReminderMail);
        var p4 = then(p3, Flow.ServiceTaskSendReminderMail.Next::endEventCustomerReminded);

        assertThat(p4.getIds()).containsExactly(
            "gateway_isSolvent",
            "timer_signatureReminder",
            "serviceTask_sendReminderMail",
            "endEvent_customerReminded"
        );
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    void parallelBranchesAssertAsAnUnorderedSetViaNodesOf() {
        var orderStart = ProcessPath.from(Flow.gatewayFork());
        var o1 = then(orderStart, Flow.GatewayFork.Next::serviceTaskOrderBike);
        var o2 = then(o1, Flow.ServiceTaskOrderBike.Next::gatewayJoin);
        var o3 = then(o2, Flow.GatewayJoin.Next::receiveTaskHandoverReported);
        List<FlowNode> orderBranch = o3.getNodes();

        var insuranceStart = ProcessPath.from(Flow.gatewayFork());
        var s1 = then(insuranceStart, Flow.GatewayFork.Next::serviceTaskIssueInsurancePolicy);
        var s2 = then(s1, Flow.ServiceTaskIssueInsurancePolicy.Next::gatewayJoin);
        var s3 = then(s2, Flow.GatewayJoin.Next::receiveTaskHandoverReported);
        List<FlowNode> insuranceBranch = s3.getNodes();

        var ids = nodesOf(orderBranch, insuranceBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        // nodesOf unions the branches and de-duplicates the shared join nodes by ElementId — the same as
        // Kotlin, now that AbstractFlowNode has id-based equals/hashCode (the Java accessors return fresh
        // instances, but equal-by-id ones).
        assertThat(ids)
            .contains("serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join")
            .doesNotHaveDuplicates();
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranchInOneChain() {

        var p0 = ProcessPath.from(Flow.gatewayFork());
        var p1 = then(p0, Flow.GatewayFork.Next::serviceTaskOrderBike);
        var p2 = jumpTo(p1, Flow.gatewayFork());
        var p3 = then(p2, Flow.GatewayFork.Next::serviceTaskIssueInsurancePolicy);
        var p4 = then(p3, Flow.ServiceTaskIssueInsurancePolicy.Next::gatewayJoin);
        var p5 = then(p4, Flow.GatewayJoin.Next::receiveTaskHandoverReported);

        var ids = p5.getNodes().stream().map(n -> n.getId().getValue()).toList();
        assertThat(ids).containsExactly(
            "gateway_fork",
            "serviceTask_orderBike",
            "serviceTask_issueInsurancePolicy",
            "gateway_join",
            "receiveTask_handoverReported"
        );
    }

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    void nodesExposeTheirIdAndFlatElementTypeAcrossElementKinds() {
        assertThat(Flow.startEventLeasingRequestReceived().getElementType()).isEqualTo(BpmnElementType.START_EVENT);
        assertThat(Flow.startEventLeasingRequestReceived().getEventType()).isEqualTo(BpmnEventType.MESSAGE);
        assertThat(Flow.gatewayFork().getElementType()).isEqualTo(BpmnElementType.PARALLEL_GATEWAY);
        assertThat(Flow.callActivityCancelBikeOrder().getElementType()).isEqualTo(BpmnElementType.CALL_ACTIVITY);
        assertThat(Flow.serviceTaskSendContract().getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
        assertThat(Flow.gatewayFork().getId().getValue()).isEqualTo("gateway_fork");
    }

    @Test
    void nodesExposeTheirDisplayNameOutgoingSequenceFlowsAndTheirOwnFacets() {
        assertThat(Flow.receiveTaskHandoverReported().getName()).isEqualTo("Await bike handover");
        assertThat(Flow.gatewayFork().getName()).isNull();

        var flow = Flow.startEventLeasingRequestReceived().getOutgoingFlows().toServiceTaskValidateApplication();
        assertThat(flow.getId().getValue()).isEqualTo("flow_leasingRequestReceivedToValidateApplication");
        assertThat(flow.getTarget()).isEqualTo(Flow.serviceTaskValidateApplication());
        assertThat(flow.getConditionExpression()).isNull();
        assertThat(flow.isDefault()).isFalse();
        assertThat(flow).isEqualTo(Flow.startEventLeasingRequestReceived().getOutgoingFlows().toServiceTaskValidateApplication());
        assertThat(Flow.timerSignatureReminder()).isInstanceOf(BoundaryEvent.class);

        VariableName.Input input = Flow.ServiceTaskSendContract.Variables.APPLICATION_ID;
        assertThat(input.getValue()).isEqualTo("applicationId");
        assertThat(Flow.ServiceTaskValidateApplication.JOB_TYPE).isEqualTo("${validateApplicationDelegate}");
        assertThat(Flow.StartEventLeasingRequestReceived.MESSAGE).isEqualTo(new MessageName("miravelo.leasingRequestReceived"));

        assertThat(Flow.TimerSignatureReminder.TIMER).isEqualTo(new BpmnTimer(TimerType.DURATION, "P7D"));
        assertThat(Flow.timerSignatureReminder().getAttachedTo()).isEqualTo(Flow.subProcessConcludeContract());
        assertThat(Flow.timerSignatureReminder().isInterrupting()).isFalse();

        assertThat(Flow.CallActivityCancelBikeOrder.CALLED_PROCESS).isEqualTo(new ProcessId("cancelBikeOrder"));
        assertThat(Flow.CallActivityCancelBikeOrder.Inputs.ORDER_IDS.getTarget()).isEqualTo("orderIds");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface VariableAnnotation {
        String name();
    }

    @Test
    @VariableAnnotation(name = Flow.ServiceTaskSendContract.Variables.Names.APPLICATION_ID)
    void rawNamesAreCompileTimeConstantsUsableInAnnotationsAndSwitchLabels() {
        assertThat(describe("serviceTask_sendContract")).isEqualTo("send contract");
        assertThat(describe("bikeLeasing")).isEqualTo("process");
        assertThat(Flow.serviceTaskSendContract().getId().getValue()).isEqualTo(Flow.ServiceTaskSendContract.ELEMENT_ID);
        assertThat(Flow.ServiceTaskSendContract.Variables.APPLICATION_ID.getValue())
            .isEqualTo(Flow.ServiceTaskSendContract.Variables.Names.APPLICATION_ID);
    }

    private static String describe(String elementId) {
        return switch (elementId) {
            case Flow.ServiceTaskSendContract.ELEMENT_ID -> "send contract";
            case BikeLeasingProcessApi.Names.PROCESS_ID -> "process";
            default -> "other";
        };
    }

    @Test
    void compensationHandlerIsReachableOnlyByNameNotThroughTheNavigationGraph() {
        var handler = Flow.serviceTaskCancelContract();
        assertThat(handler.getId().getValue()).isEqualTo("serviceTask_cancelContract");
        assertThat(handler.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    }
}
