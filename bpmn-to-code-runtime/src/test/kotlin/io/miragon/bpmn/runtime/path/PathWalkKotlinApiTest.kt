package io.miragon.bpmn.runtime.path

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow as BikeLeasing

/**
 * Exercises the fluent [PathWalk] facade over the generated bike-leasing API from **Kotlin** (its
 * `java.util.function.Function` picks SAM-convert to `{ it.x }`). The `PathWalkJavaApiTest` sibling runs the
 * *identical* cases and paths from Java; Kotlin code normally prefers the extension DSL — see
 * [ProcessPathKotlinApiTest], which also holds the API-agnostic node-metadata / compensation checks (not
 * duplicated here).
 */
class PathWalkKotlinApiTest {

    @Test
    fun `happy path walks the subprocess interior via inside`() {
        val ids = PathWalk.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then { it.serviceTaskValidateApplication }
            .then { it.businessRuleTaskCheckCreditRating }
            .then { it.gatewayIsSolvent }
            .onto { it.subProcessConcludeContract }
            .inside(BikeLeasing.SubProcessConcludeContract) { s ->
                PathWalk.from(s.startEventCustomerEligible)
                    .then { it.serviceTaskSendContract }
                    .then { it.gatewayAwaitSignature }
                    .then { it.eventContractSigned }
                    .end { it.endEventContractConcluded }
            }
            .then { it.gatewayFork }
            .then { it.serviceTaskOrderBike }
            .then { it.gatewayJoin }
            .then { it.receiveTaskHandoverReported }
            .then { it.timerWithdrawalPeriodElapsed }
            .end { it.endEventLeasingActive }
            .ids

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
            "endEvent_leasingActive",
        )
    }

    @Test
    fun `via records the walked sequence flows including those of the subprocess interior`() {
        val flowIds = PathWalk.from(BikeLeasing.StartEventLeasingRequestReceived)
            .via { it.outgoingFlows().toServiceTaskValidateApplication }
            .via { it.outgoingFlows().toBusinessRuleTaskCheckCreditRating }
            .via { it.outgoingFlows().toGatewayIsSolvent }
            .via { it.outgoingFlows().toSubProcessConcludeContract }
            .inside(BikeLeasing.SubProcessConcludeContract) { s ->
                PathWalk.from(s.startEventCustomerEligible)
                    .via { it.outgoingFlows().toServiceTaskSendContract }
                    .via { it.outgoingFlows().toGatewayAwaitSignature }
                    .via { it.outgoingFlows().toEventContractSigned }
                    .endVia { it.outgoingFlows().toEndEventContractConcluded }
            }
            .via { it.outgoingFlows().toGatewayFork }
            .via { it.outgoingFlows().toServiceTaskOrderBike }
            .via { it.outgoingFlows().toGatewayJoin }
            .via { it.outgoingFlows().toReceiveTaskHandoverReported }
            .via { it.outgoingFlows().toTimerWithdrawalPeriodElapsed }
            .endVia { it.outgoingFlows().toEndEventLeasingActive }
            .flowIds

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
            "flow_withdrawalPeriodElapsedToLeasingActive",
        )
    }

    @Test
    fun `escalation boundary leaves the subprocess into the terminate end`() {
        val ids = PathWalk.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then { it.serviceTaskValidateApplication }
            .then { it.businessRuleTaskCheckCreditRating }
            .enter(BikeLeasing.SubProcessConcludeContract) { it.startEventCustomerEligible }
            .then { it.serviceTaskSendContract }
            .then { it.gatewayAwaitSignature }
            .then { it.timerSignatureDeadline }
            .interruptedBy(BikeLeasing.SubProcessConcludeContract) { it.boundaryContractNotSigned }
            .then { it.gatewayCollectRejections }
            .then { it.serviceTaskSendRejection }
            .end { it.endEventApplicationRejected }
            .ids

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
            "endEvent_applicationRejected",
        )
    }

    @Test
    fun `error boundary on a task is a successor of the task`() {
        val ids = PathWalk.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then { it.serviceTaskValidateApplication }
            .then { it.boundaryApplicationInvalid }
            .then { it.gatewayCollectRejections }
            .then { it.serviceTaskSendRejection }
            .end { it.endEventApplicationRejected }
            .ids

        assertThat(ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "boundary_applicationInvalid",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected",
        )
    }

    @Test
    fun `parallel branches union into a deduplicated set via nodesOf`() {
        val orderBranch = PathWalk.from(BikeLeasing.GatewayFork)
            .then { it.serviceTaskOrderBike }
            .then { it.gatewayJoin }
            .then { it.receiveTaskHandoverReported }
            .nodes
        val insuranceBranch = PathWalk.from(BikeLeasing.GatewayFork)
            .then { it.serviceTaskIssueInsurancePolicy }
            .then { it.gatewayJoin }
            .then { it.receiveTaskHandoverReported }
            .nodes

        assertThat(PathWalk.nodesOf(orderBranch, insuranceBranch).map { it.id.value })
            .contains("serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join")
            .doesNotHaveDuplicates()
    }

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch`() {
        val ids = PathWalk.from(BikeLeasing.GatewayFork)
            .then { it.serviceTaskOrderBike }
            .jumpTo(BikeLeasing.GatewayFork)
            .then { it.serviceTaskIssueInsurancePolicy }
            .then { it.gatewayJoin }
            .then { it.receiveTaskHandoverReported }
            .ids

        assertThat(ids).containsExactly(
            "gateway_fork",
            "serviceTask_orderBike",
            "serviceTask_issueInsurancePolicy",
            "gateway_join",
            "receiveTask_handoverReported",
        )
    }

    @Test
    fun `thenMultipleTimes records the same node repeatedly (builder mechanic, not a real flow)`() {
        // Bike leasing has no consecutively-repeating node, so this is an isolated mechanic check that also
        // exercises the instance-level nodes / ids / distinctIds accessors (mid-walk, before any end).
        val walk = PathWalk.from(BikeLeasing.GatewayFork)
            .thenMultipleTimes(2) { it.serviceTaskOrderBike }

        assertThat(walk.ids)
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike")
        assertThat(walk.distinctIds).containsExactly("gateway_fork", "serviceTask_orderBike")
        assertThat(walk.nodes.map { it.id.value })
            .containsExactly("gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike")
    }
}
