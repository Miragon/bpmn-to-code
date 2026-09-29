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
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.FlowNodes;
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

    // --- Singleton nodes -----------------------------------------------------------------------------------

    @Test
    void everyAccessToANodeYieldsTheSameInstance() {
        assertThat(FlowNodes.gatewayJoin()).isSameAs(FlowNodes.gatewayJoin());
        assertThat(FlowNodes.serviceTaskOrderBike().getNext().gatewayJoin()).isSameAs(FlowNodes.GatewayJoin.INSTANCE);
        assertThat(FlowNodes.gatewayJoin()).hasToString("PARALLEL_GATEWAY(gateway_join)");
    }

    // --- Sequential flow through a subprocess -------------------------------------------------------------

    @Test
    void happyPathWalksTheSubprocessInteriorWithInsideAndContinuesCheckedAfterIt() {

        var p0 = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var p1 = then(p0, FlowNodes.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, FlowNodes.ServiceTaskValidateApplication.Next::businessRuleTaskCheckCreditRating);
        var p3 = then(p2, FlowNodes.BusinessRuleTaskCheckCreditRating.Next::gatewayIsSolvent);
        var p4 = onto(p3, FlowNodes.GatewayIsSolvent.Next::subProcessConcludeContract);
        var p5 = inside(p4, sub -> {
            var i0 = enter(sub, FlowNodes.SubProcessConcludeContract.Start::startEventCustomerEligible);
            var i1 = then(i0, FlowNodes.StartEventCustomerEligible.Next::serviceTaskSendContract);
            var i2 = then(i1, FlowNodes.ServiceTaskSendContract.Next::gatewayAwaitSignature);
            var i3 = then(i2, FlowNodes.GatewayAwaitSignature.Next::eventContractSigned);
            return then(i3, FlowNodes.EventContractSigned.Next::endEventContractConcluded);
        });
        var p6 = then(p5, FlowNodes.SubProcessConcludeContract.Next::gatewayFork);
        var p7 = then(p6, FlowNodes.GatewayFork.Next::serviceTaskOrderBike);
        var p8 = then(p7, FlowNodes.ServiceTaskOrderBike.Next::gatewayJoin);
        var p9 = then(p8, FlowNodes.GatewayJoin.Next::receiveTaskHandoverReported);
        var p10 = then(p9, FlowNodes.ReceiveTaskHandoverReported.Next::timerWithdrawalPeriodElapsed);
        var p11 = then(p10, FlowNodes.TimerWithdrawalPeriodElapsed.Next::endEventLeasingActive);

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
        var p0 = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var p1 = then(p0, FlowNodes.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, FlowNodes.ServiceTaskValidateApplication.Next::businessRuleTaskCheckCreditRating);
        var p3 = enter(p2, FlowNodes.subProcessConcludeContract(), FlowNodes.SubProcessConcludeContract.Start::startEventCustomerEligible);
        var p4 = then(p3, FlowNodes.StartEventCustomerEligible.Next::serviceTaskSendContract);
        var p5 = then(p4, FlowNodes.ServiceTaskSendContract.Next::gatewayAwaitSignature);
        var p6 = then(p5, FlowNodes.GatewayAwaitSignature.Next::timerSignatureDeadline);
        var p7 = then(p6, FlowNodes.TimerSignatureDeadline.Next::endEventContractNotSigned);
        var p8 = interruptedBy(p7, FlowNodes.subProcessConcludeContract(), FlowNodes.SubProcessConcludeContract.Next::boundaryContractNotSigned);
        var p9 = then(p8, FlowNodes.BoundaryContractNotSigned.Next::gatewayCollectRejections);
        var p10 = then(p9, FlowNodes.GatewayCollectRejections.Next::serviceTaskSendRejection);
        var p11 = then(p10, FlowNodes.ServiceTaskSendRejection.Next::endEventApplicationRejected);

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
        var p0 = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var p1 = then(p0, FlowNodes.StartEventLeasingRequestReceived.Next::serviceTaskValidateApplication);
        var p2 = then(p1, FlowNodes.ServiceTaskValidateApplication.Next::boundaryApplicationInvalid);
        var p3 = then(p2, FlowNodes.BoundaryApplicationInvalid.Next::gatewayCollectRejections);
        var p4 = then(p3, FlowNodes.GatewayCollectRejections.Next::serviceTaskSendRejection);
        var p5 = then(p4, FlowNodes.ServiceTaskSendRejection.Next::endEventApplicationRejected);

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
        var p0 = ProcessPath.from(FlowNodes.gatewayIsSolvent());
        var p1 = onto(p0, FlowNodes.GatewayIsSolvent.Next::subProcessConcludeContract);
        var p2 = then(p1, FlowNodes.SubProcessConcludeContract.Next::timerSignatureReminder);
        var p3 = then(p2, FlowNodes.TimerSignatureReminder.Next::serviceTaskSendReminderMail);
        var p4 = then(p3, FlowNodes.ServiceTaskSendReminderMail.Next::endEventCustomerReminded);

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
        var orderStart = ProcessPath.from(FlowNodes.gatewayFork());
        var o1 = then(orderStart, FlowNodes.GatewayFork.Next::serviceTaskOrderBike);
        var o2 = then(o1, FlowNodes.ServiceTaskOrderBike.Next::gatewayJoin);
        var o3 = then(o2, FlowNodes.GatewayJoin.Next::receiveTaskHandoverReported);
        List<FlowNode> orderBranch = o3.getNodes();

        var insuranceStart = ProcessPath.from(FlowNodes.gatewayFork());
        var s1 = then(insuranceStart, FlowNodes.GatewayFork.Next::serviceTaskIssueInsurancePolicy);
        var s2 = then(s1, FlowNodes.ServiceTaskIssueInsurancePolicy.Next::gatewayJoin);
        var s3 = then(s2, FlowNodes.GatewayJoin.Next::receiveTaskHandoverReported);
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

        var p0 = ProcessPath.from(FlowNodes.gatewayFork());
        var p1 = then(p0, FlowNodes.GatewayFork.Next::serviceTaskOrderBike);
        var p2 = jumpTo(p1, FlowNodes.gatewayFork());
        var p3 = then(p2, FlowNodes.GatewayFork.Next::serviceTaskIssueInsurancePolicy);
        var p4 = then(p3, FlowNodes.ServiceTaskIssueInsurancePolicy.Next::gatewayJoin);
        var p5 = then(p4, FlowNodes.GatewayJoin.Next::receiveTaskHandoverReported);

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
        assertThat(FlowNodes.startEventLeasingRequestReceived().getElementType()).isEqualTo(BpmnElementType.START_EVENT);
        assertThat(FlowNodes.startEventLeasingRequestReceived().getEventType()).isEqualTo(BpmnEventType.MESSAGE);
        assertThat(FlowNodes.gatewayFork().getElementType()).isEqualTo(BpmnElementType.PARALLEL_GATEWAY);
        assertThat(FlowNodes.callActivityCancelBikeOrder().getElementType()).isEqualTo(BpmnElementType.CALL_ACTIVITY);
        assertThat(FlowNodes.serviceTaskSendContract().getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
        assertThat(FlowNodes.gatewayFork().getId().getValue()).isEqualTo("gateway_fork");
    }

    @Test
    void nodesExposeTheirDisplayNameOutgoingSequenceFlowsAndTheirOwnFacets() {
        assertThat(FlowNodes.receiveTaskHandoverReported().getName()).isEqualTo("Await bike handover");
        assertThat(FlowNodes.gatewayFork().getName()).isNull();

        var flow = FlowNodes.startEventLeasingRequestReceived().getOutgoingFlows().toServiceTaskValidateApplication();
        assertThat(flow.getId().getValue()).isEqualTo("flow_leasingRequestReceivedToValidateApplication");
        assertThat(flow.getTarget()).isEqualTo(FlowNodes.serviceTaskValidateApplication());
        assertThat(flow.getConditionExpression()).isNull();
        assertThat(flow.isDefault()).isFalse();
        assertThat(flow).isEqualTo(FlowNodes.startEventLeasingRequestReceived().getOutgoingFlows().toServiceTaskValidateApplication());
        assertThat(FlowNodes.timerSignatureReminder()).isInstanceOf(BoundaryEvent.class);

        VariableName.Input input = FlowNodes.ServiceTaskSendContract.Variables.APPLICATION_ID;
        assertThat(input.getValue()).isEqualTo("applicationId");
        assertThat(FlowNodes.ServiceTaskValidateApplication.JOB_TYPE).isEqualTo("${validateApplicationDelegate}");
        assertThat(FlowNodes.StartEventLeasingRequestReceived.MESSAGE).isEqualTo(new MessageName("miravelo.leasingRequestReceived"));

        assertThat(FlowNodes.TimerSignatureReminder.TIMER).isEqualTo(new BpmnTimer(TimerType.DURATION, "P7D"));
        assertThat(FlowNodes.timerSignatureReminder().getAttachedTo()).isEqualTo(FlowNodes.subProcessConcludeContract());
        assertThat(FlowNodes.timerSignatureReminder().isInterrupting()).isFalse();

        assertThat(FlowNodes.CallActivityCancelBikeOrder.CALLED_PROCESS).isEqualTo(new ProcessId("cancelBikeOrder"));
        assertThat(FlowNodes.CallActivityCancelBikeOrder.Inputs.ORDER_IDS.getTarget()).isEqualTo("orderIds");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface VariableAnnotation {
        String name();
    }

    @Test
    @VariableAnnotation(name = FlowNodes.ServiceTaskSendContract.Variables.Names.APPLICATION_ID)
    void rawNamesAreCompileTimeConstantsUsableInAnnotationsAndSwitchLabels() {
        assertThat(describe("serviceTask_sendContract")).isEqualTo("send contract");
        assertThat(describe("bikeLeasing")).isEqualTo("process");
        assertThat(FlowNodes.serviceTaskSendContract().getId().getValue()).isEqualTo(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        assertThat(FlowNodes.ServiceTaskSendContract.Variables.APPLICATION_ID.getValue())
            .isEqualTo(FlowNodes.ServiceTaskSendContract.Variables.Names.APPLICATION_ID);
    }

    private static String describe(String elementId) {
        return switch (elementId) {
            case FlowNodes.ServiceTaskSendContract.ELEMENT_ID -> "send contract";
            case BikeLeasingProcessApi.Names.PROCESS_ID -> "process";
            default -> "other";
        };
    }

    @Test
    void compensationHandlerIsReachableOnlyByNameNotThroughTheNavigationGraph() {
        var handler = FlowNodes.serviceTaskCancelContract();
        assertThat(handler.getId().getValue()).isEqualTo("serviceTask_cancelContract");
        assertThat(handler.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    }
}
