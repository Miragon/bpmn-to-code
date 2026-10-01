package io.miragon.bpmn.runtime.path;

import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.FlowNodes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.enter;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.inside;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.interruptedBy;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.jumpTo;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.nodesOf;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.onto;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.then;
import static io.miragon.bpmn.runtime.path.ProcessPathStepsKt.thenMultipleTimes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@link ProcessPath} steps over the generated Java bike-leasing API from <em>Java</em>. A step is a
 * static call taking the path and the target node, so a walk is written one step per statement. The compile-time
 * edge check is intact: a target has to implement the current node's {@code SuccessorOf} marker.
 */
class ProcessPathJavaApiTest {

    @Test
    void thenRecordsTheWalkedSequenceFlowsNextToTheElements() {
        var creditRating = ProcessPath.from(FlowNodes.businessRuleTaskCheckCreditRating());
        var isSolvent = then(creditRating, FlowNodes.gatewayIsSolvent());
        var concludeContract = then(isSolvent, FlowNodes.subProcessConcludeContract());

        assertThat(concludeContract.getIds()).containsExactly(
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "subProcess_concludeContract"
        );
        assertThat(concludeContract.getFlowIds()).containsExactly("flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract");
    }

    @Test
    void flowsToReadsTheFlowDataOfAnEdge() {
        var toConcludeContract = FlowNodes.gatewayIsSolvent().flowsTo(FlowNodes.subProcessConcludeContract());

        assertThat(toConcludeContract.getTarget()).isSameAs(FlowNodes.subProcessConcludeContract());
        assertThat(toConcludeContract.getFlow().isDefault()).isTrue();
    }

    @Test
    void happyPathWalksTheSubprocessInteriorViaInside() {
        var start = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var validated = then(start, FlowNodes.serviceTaskValidateApplication());
        var rated = then(validated, FlowNodes.businessRuleTaskCheckCreditRating());
        var solvent = then(rated, FlowNodes.gatewayIsSolvent());
        var contract = onto(solvent, FlowNodes.subProcessConcludeContract());
        var concluded = inside(contract, subProcess -> {
            var eligible = enter(subProcess, interior -> interior.startEventCustomerEligible());
            var sent = then(eligible, FlowNodes.serviceTaskSendContract());
            var awaiting = then(sent, FlowNodes.gatewayAwaitSignature());
            var signed = then(awaiting, FlowNodes.eventContractSigned());
            return then(signed, FlowNodes.endEventContractConcluded());
        });
        var fork = then(concluded, FlowNodes.gatewayFork());
        var ordered = then(fork, FlowNodes.serviceTaskOrderBike());
        var join = then(ordered, FlowNodes.gatewayJoin());
        var handover = then(join, FlowNodes.receiveTaskHandoverReported());
        var withdrawalElapsed = then(handover, FlowNodes.timerWithdrawalPeriodElapsed());
        var active = then(withdrawalElapsed, FlowNodes.endEventLeasingActive());

        assertThat(active.getIds()).containsExactly(
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
        assertThat(active.getFlowIds()).containsExactly(
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
    void escalationBoundaryLeavesTheSubprocessIntoTheTerminateEnd() {
        var start = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var validated = then(start, FlowNodes.serviceTaskValidateApplication());
        var rated = then(validated, FlowNodes.businessRuleTaskCheckCreditRating());
        var eligible = enter(rated, FlowNodes.subProcessConcludeContract(), interior -> interior.startEventCustomerEligible());
        var sent = then(eligible, FlowNodes.serviceTaskSendContract());
        var awaiting = then(sent, FlowNodes.gatewayAwaitSignature());
        var deadline = then(awaiting, FlowNodes.timerSignatureDeadline());
        var notSigned = interruptedBy(deadline, FlowNodes.subProcessConcludeContract(), FlowNodes.boundaryContractNotSigned());
        var rejections = then(notSigned, FlowNodes.gatewayCollectRejections());
        var rejectionSent = then(rejections, FlowNodes.serviceTaskSendRejection());
        var rejected = then(rejectionSent, FlowNodes.endEventApplicationRejected());

        assertThat(rejected.getIds()).containsExactly(
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
        var start = ProcessPath.from(FlowNodes.startEventLeasingRequestReceived());
        var validated = then(start, FlowNodes.serviceTaskValidateApplication());
        var invalid = then(validated, FlowNodes.boundaryApplicationInvalid());
        var rejections = then(invalid, FlowNodes.gatewayCollectRejections());
        var rejectionSent = then(rejections, FlowNodes.serviceTaskSendRejection());
        var rejected = then(rejectionSent, FlowNodes.endEventApplicationRejected());

        assertThat(rejected.getIds()).containsExactly(
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
        var fork = ProcessPath.from(FlowNodes.gatewayFork());
        List<FlowNode> orderBranch = then(then(then(fork, FlowNodes.serviceTaskOrderBike()), FlowNodes.gatewayJoin()), FlowNodes.receiveTaskHandoverReported()).getNodes();
        List<FlowNode> insuranceBranch = then(then(then(fork, FlowNodes.serviceTaskIssueInsurancePolicy()), FlowNodes.gatewayJoin()), FlowNodes.receiveTaskHandoverReported()).getNodes();

        var ids = nodesOf(orderBranch, insuranceBranch).stream()
            .map(n -> n.getId().getValue())
            .toList();

        assertThat(ids)
            .contains("serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join")
            .doesNotHaveDuplicates();
    }

    @Test
    void jumpToReAnchorsToTheForkToWalkTheSecondParallelBranch() {
        // RiskyNavigation is not enforced for Java callers (no @OptIn equivalent) — the intent is documented.
        var ordered = then(ProcessPath.from(FlowNodes.gatewayFork()), FlowNodes.serviceTaskOrderBike());
        var fork = jumpTo(ordered, FlowNodes.gatewayFork());
        var insured = then(fork, FlowNodes.serviceTaskIssueInsurancePolicy());
        var join = then(insured, FlowNodes.gatewayJoin());
        var handover = then(join, FlowNodes.receiveTaskHandoverReported());

        assertThat(handover.getIds()).containsExactly(
            "gateway_fork",
            "serviceTask_orderBike",
            "serviceTask_issueInsurancePolicy",
            "gateway_join",
            "receiveTask_handoverReported"
        );
    }

    @Test
    void thenMultipleTimesRecordsTheSameNodeRepeatedly() {
        var path = thenMultipleTimes(ProcessPath.from(FlowNodes.gatewayFork()), 2, FlowNodes.serviceTaskOrderBike());

        assertThat(path.getIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike");
        assertThat(path.getDistinctIds())
            .containsExactly("gateway_fork", "serviceTask_orderBike");
    }
}
