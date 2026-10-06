package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.FlowNodes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the fluent {@link PathWalk} facade over the generated Java bike-leasing API from <em>Java</em> — the
 * recommended fluent form for Java consumers. Runs the same cases and paths as {@code ProcessPathKotlinApiTest}.
 * The API-agnostic node-metadata checks live once in that test and are not duplicated here. The compile-time edge check is intact: {@code n} is the current node's {@code Next}.
 */
class PathWalkJavaApiTest {

    @Test
    void thenRecordsTheWalkedSequenceFlowsNextToTheElements() {
        var walk = PathWalk.from(FlowNodes.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent())
            .then(n -> n.subProcessConcludeContract());

        assertThat(walk.getIds()).containsExactly(
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "subProcess_concludeContract"
        );
        assertThat(walk.getFlowIds()).containsExactly("flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract");
    }

    @Test
    void thenRecordsTheWalkedSequenceFlowsIncludingThoseOfTheSubprocessInterior() {
        var flowIds = PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent())
            .then(n -> n.subProcessConcludeContract())
            .inside(FlowNodes.subProcessConcludeContract(), s ->
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
        var ids = PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent())
            .onto(n -> n.subProcessConcludeContract())
            .inside(FlowNodes.subProcessConcludeContract(), s ->
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
        var ids = PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .enter(FlowNodes.subProcessConcludeContract(), s -> s.startEventCustomerEligible())
            .then(n -> n.serviceTaskSendContract())
            .then(n -> n.gatewayAwaitSignature())
            .then(n -> n.timerSignatureDeadline())
            .interruptedBy(FlowNodes.subProcessConcludeContract(), n -> n.boundaryContractNotSigned())
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
    void compensatedPathRecordsEachCompensationWithItsBoundaryEventAfterTheEventThatThrowsItAndContinuesFromThatEvent() {
        var trail = PathWalk.from(FlowNodes.startEventApplicationWithdrawn())
            .then(n -> n.eventReverseApplication())
            .throwingCompensation(FlowNodes.boundaryCompensateContract(), true, n -> n.serviceTaskCancelContract())
            .throwingCompensation(FlowNodes.boundaryCompensateOrder(), true, n -> n.callActivityCancelBikeOrder())
            .then(n -> n.serviceTaskSendCancellationConfirmation())
            .end(n -> n.endEventApplicationCancelled());

        assertThat(trail.getIds()).containsExactly(
            "startEvent_applicationWithdrawn",
            "event_reverseApplication",
            "boundary_compensateContract",
            "serviceTask_cancelContract",
            "boundary_compensateOrder",
            "callActivity_cancelBikeOrder",
            "serviceTask_sendCancellationConfirmation",
            "endEvent_applicationCancelled"
        );
        assertThat(trail.getFlowIds()).containsExactly(
            "flow_applicationWithdrawnToReverseApplication",
            "flow_reverseApplicationToSendCancellationConfirmation",
            "flow_sendCancellationConfirmationToApplicationCancelled"
        );
    }

    @Test
    void compensationHandlerIsRecordedWithoutItsBoundaryEventByDefault() {
        var ids = PathWalk.from(FlowNodes.startEventApplicationWithdrawn())
            .then(n -> n.eventReverseApplication())
            .throwingCompensation(FlowNodes.boundaryCompensateInsurance(), n -> n.serviceTaskCancelPolicy())
            .getIds();

        assertThat(ids).containsExactly("startEvent_applicationWithdrawn", "event_reverseApplication", "serviceTask_cancelPolicy");
    }

    @Test
    void trailRecordsACompensationAfterItsTerminalNode() {
        var trail = PathWalk.from(FlowNodes.serviceTaskSendCancellationConfirmation())
            .end(n -> n.endEventApplicationCancelled())
            .throwingCompensation(FlowNodes.boundaryCompensateContract(), true, n -> n.serviceTaskCancelContract())
            .throwingCompensation(FlowNodes.boundaryCompensateOrder(), n -> n.callActivityCancelBikeOrder());

        assertThat(trail.getIds()).containsExactly(
            "serviceTask_sendCancellationConfirmation",
            "endEvent_applicationCancelled",
            "boundary_compensateContract",
            "serviceTask_cancelContract",
            "callActivity_cancelBikeOrder"
        );
    }

    @Test
    void errorBoundaryOnATaskIsASuccessorOfTheTask() {
        var ids = PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
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
        List<FlowNode> orderBranch = PathWalk.from(FlowNodes.gatewayFork())
            .then(n -> n.serviceTaskOrderBike())
            .then(n -> n.gatewayJoin())
            .then(n -> n.receiveTaskHandoverReported())
            .getNodes();
        List<FlowNode> insuranceBranch = PathWalk.from(FlowNodes.gatewayFork())
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
        var ids = PathWalk.from(FlowNodes.gatewayFork())
            .then(n -> n.serviceTaskOrderBike())
            .jumpTo(FlowNodes.gatewayFork())
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
        var walk = PathWalk.from(FlowNodes.gatewayFork())
            .thenMultipleTimes(2, n -> n.serviceTaskOrderBike());

        assertThat(walk.getIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike");
        assertThat(walk.getDistinctIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike");
        assertThat(walk.getNodes().stream().map(n -> n.getId().getValue()).toList())
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike");
    }
}
