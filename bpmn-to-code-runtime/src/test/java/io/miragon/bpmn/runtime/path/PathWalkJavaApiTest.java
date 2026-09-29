package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.Flow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the fluent {@link PathWalk} facade over the generated Java bike-leasing API from <em>Java</em> — the
 * recommended fluent form for Java consumers. Runs the <em>identical</em> cases and paths as
 * {@code PathWalkKotlinApiTest}. The API-agnostic node-metadata / compensation checks live once in the
 * extension-DSL tests ({@code ProcessPathKotlinApiTest} / {@code ProcessPathJavaApiTest}) and are not
 * duplicated here. The compile-time edge check is intact: {@code n} is the current node's {@code Next}.
 */
class PathWalkJavaApiTest {

    @Test
    void viaWalksChosenSequenceFlowsAndRecordsThemNextToTheElements() {
        var walk = PathWalk.from(Flow.businessRuleTaskCheckCreditRating())
            .via(n -> n.getOutgoingFlows().toGatewayIsSolvent())
            .via(n -> n.getOutgoingFlows().toSubProcessConcludeContract());

        assertThat(walk.getIds()).containsExactly(
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "subProcess_concludeContract"
        );
        assertThat(walk.getFlowIds()).containsExactly("flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract");
    }

    @Test
    void viaRecordsTheWalkedSequenceFlowsIncludingThoseOfTheSubprocessInterior() {
        var flowIds = PathWalk.from(Flow.startEventLeasingRequestReceived())
            .via(n -> n.getOutgoingFlows().toServiceTaskValidateApplication())
            .via(n -> n.getOutgoingFlows().toBusinessRuleTaskCheckCreditRating())
            .via(n -> n.getOutgoingFlows().toGatewayIsSolvent())
            .via(n -> n.getOutgoingFlows().toSubProcessConcludeContract())
            .inside(Flow.subProcessConcludeContract(), s ->
                PathWalk.from(s.startEventCustomerEligible())
                    .via(n -> n.getOutgoingFlows().toServiceTaskSendContract())
                    .via(n -> n.getOutgoingFlows().toGatewayAwaitSignature())
                    .via(n -> n.getOutgoingFlows().toEventContractSigned())
                    .endVia(n -> n.getOutgoingFlows().toEndEventContractConcluded()))
            .via(n -> n.getOutgoingFlows().toGatewayFork())
            .via(n -> n.getOutgoingFlows().toServiceTaskOrderBike())
            .via(n -> n.getOutgoingFlows().toGatewayJoin())
            .via(n -> n.getOutgoingFlows().toReceiveTaskHandoverReported())
            .via(n -> n.getOutgoingFlows().toTimerWithdrawalPeriodElapsed())
            .endVia(n -> n.getOutgoingFlows().toEndEventLeasingActive())
            .getFlowIds();

        assertThat(flowIds).containsExactly(
            "flow_leasingRequestReceivedToValidateApplication",
            "flow_validateApplicationToCheckCreditRating",
            "flow_checkCreditRatingToIsSolvent",
            "flow_isSolventToConcludeContract",
            "flow_customerEligibleToSendContract",
            "flow_sendContractToAwaitSignature",
            "flow_awaitSignatureToContractSigned",
            "flow_contractSignedToContractConcluded",
            "flow_concludeContractToFork",
            "flow_forkToOrderBike",
            "flow_orderBikeToJoin",
            "flow_joinToHandoverReported",
            "flow_handoverReportedToWithdrawalPeriodElapsed",
            "flow_withdrawalPeriodElapsedToLeasingActive"
        );
    }

    @Test
    void happyPathWalksTheSubprocessInteriorViaInside() {
        var ids = PathWalk.from(Flow.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent())
            .onto(n -> n.subProcessConcludeContract())
            .inside(Flow.subProcessConcludeContract(), s ->
                PathWalk.from(s.startEventCustomerEligible())
                    .then(n -> n.serviceTaskSendContract())
                    .then(n -> n.gatewayAwaitSignature())
                    .then(n -> n.eventContractSigned())
                    .end(n -> n.endEventContractConcluded()))
            .then(n -> n.gatewayFork())
            .then(n -> n.serviceTaskOrderBike())
            .then(n -> n.gatewayJoin())
            .then(n -> n.receiveTaskHandoverReported())
            .then(n -> n.timerWithdrawalPeriodElapsed())
            .end(n -> n.endEventLeasingActive())
            .getIds();

        assertThat(ids).containsExactly(
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

    @Test
    void escalationBoundaryLeavesTheSubprocessIntoTheTerminateEnd() {
        var ids = PathWalk.from(Flow.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .enter(Flow.subProcessConcludeContract(), s -> s.startEventCustomerEligible())
            .then(n -> n.serviceTaskSendContract())
            .then(n -> n.gatewayAwaitSignature())
            .then(n -> n.timerSignatureDeadline())
            .interruptedBy(Flow.subProcessConcludeContract(), n -> n.boundaryContractNotSigned())
            .then(n -> n.gatewayCollectRejections())
            .then(n -> n.serviceTaskSendRejection())
            .end(n -> n.endEventApplicationRejected())
            .getIds();

        assertThat(ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "businessRuleTask_checkCreditRating",
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "timer_signatureDeadline",
            "boundary_contractNotSigned",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected"
        );
    }

    @Test
    void errorBoundaryOnATaskIsASuccessorOfTheTask() {
        var ids = PathWalk.from(Flow.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.boundaryApplicationInvalid())
            .then(n -> n.gatewayCollectRejections())
            .then(n -> n.serviceTaskSendRejection())
            .end(n -> n.endEventApplicationRejected())
            .getIds();

        assertThat(ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "boundary_applicationInvalid",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected"
        );
    }

    @Test
    void parallelBranchesUnionIntoADeduplicatedSetViaNodesOf() {
        List<FlowNode> orderBranch = PathWalk.from(Flow.gatewayFork())
            .then(n -> n.serviceTaskOrderBike())
            .then(n -> n.gatewayJoin())
            .then(n -> n.receiveTaskHandoverReported())
            .getNodes();
        List<FlowNode> insuranceBranch = PathWalk.from(Flow.gatewayFork())
            .then(n -> n.serviceTaskIssueInsurancePolicy())
            .then(n -> n.gatewayJoin())
            .then(n -> n.receiveTaskHandoverReported())
            .getNodes();

        var ids = PathWalk.nodesOf(orderBranch, insuranceBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        assertThat(ids)
            .contains("serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join")
            .doesNotHaveDuplicates();
    }

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranch() {
        // RiskyNavigation is not enforced for Java callers (no @OptIn equivalent) — the intent is documented.
        var ids = PathWalk.from(Flow.gatewayFork())
            .then(n -> n.serviceTaskOrderBike())
            .jumpTo(Flow.gatewayFork())
            .then(n -> n.serviceTaskIssueInsurancePolicy())
            .then(n -> n.gatewayJoin())
            .then(n -> n.receiveTaskHandoverReported())
            .getIds();

        assertThat(ids).containsExactly(
            "gateway_fork",
            "serviceTask_orderBike",
            "serviceTask_issueInsurancePolicy",
            "gateway_join",
            "receiveTask_handoverReported"
        );
    }

    @Test
    void thenMultipleTimesRecordsTheSameNodeRepeatedly() {
        // Bike leasing has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        var walk = PathWalk.from(Flow.gatewayFork())
            .thenMultipleTimes(2, n -> n.serviceTaskOrderBike());

        assertThat(walk.getIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike");
        assertThat(walk.getDistinctIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike");
        assertThat(walk.getNodes().stream().map(n -> n.getId().getValue()).toList())
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike");
    }
}
