package io.miragon.bpmn.runtime

import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BoundaryApplicationInvalid
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BoundaryCompensateContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BoundaryCompensateInsurance
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BoundaryCompensateOrder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BoundaryContractNotSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.BusinessRuleTaskCheckCreditRating
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.CallActivityCancelBikeOrder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventApplicationCancelled
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventApplicationRejected
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventContractConcluded
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventContractNotSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventCustomerReminded
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventDeliveryAddressUpdated
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EndEventLeasingActive
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EventContractSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.EventReverseApplication
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.GatewayAwaitSignature
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.GatewayCollectRejections
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.GatewayFork
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.GatewayIsSolvent
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.GatewayJoin
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ReceiveTaskHandoverReported
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskCancelContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskCancelPolicy
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskIssueInsurancePolicy
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskOrderBike
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskSendCancellationConfirmation
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskSendContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskSendRejection
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskSendReminderMail
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.ServiceTaskValidateApplication
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.StartEventAddressChanged
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.StartEventApplicationWithdrawn
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.StartEventCustomerEligible
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.SubProcessAddressChanged
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.SubProcessApplicationWithdrawn
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.SubProcessConcludeContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.TimerSignatureDeadline
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.TimerSignatureReminder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.TimerWithdrawalPeriodElapsed
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.UserTaskUpdateDeliveryAddress
import io.miragon.bpmn.runtime.path.example.Errors
import io.miragon.bpmn.runtime.path.example.Escalations
import io.miragon.bpmn.runtime.path.example.Messages
import io.miragon.bpmn.runtime.path.example.ServiceTasks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Enumerates the generated bike-leasing API from **Kotlin**: `FlowNodes.entries`, the sealed `FlowNodes.Node` and the
 * shared definitions' `entries`. The `GeneratedApiEnumerationJavaTest` sibling covers the Java `all()` accessors.
 */
class GeneratedApiEnumerationKotlinTest {

    @Test
    fun `flow entries list exactly the nodes of the process`() {
        val everyNode = FlowNodes.Node::class.sealedSubclasses.map { it.objectInstance }

        assertThat(FlowNodes.entries).hasSize(42).containsExactlyInAnyOrderElementsOf(everyNode)
    }

    @Test
    fun `a when over the sealed node type is exhaustive without an else branch`() {
        assertThat(FlowNodes.entries).allSatisfy { node ->
            assertThat(kindOf(node)).describedAs(node.id.value).isEqualTo(kindOf(node.elementType))
        }
    }

    @Test
    fun `shared definitions list exactly their values in declaration order`() {
        assertThat(ServiceTasks.entries).containsExactly(
            ServiceTasks.CANCEL_CONTRACT_DELEGATE,
            ServiceTasks.MAIL_SERVICE_SEND_REMINDER_APPLICATION_ID_,
            ServiceTasks.SEND_CONTRACT_DELEGATE,
            ServiceTasks.VALIDATE_APPLICATION_DELEGATE,
            ServiceTasks.IO_MIRAVELO_LEASING_ISSUE_INSURANCE_POLICY_DELEGATE,
            ServiceTasks.MIRAVELO_CANCEL_POLICY,
            ServiceTasks.MIRAVELO_ORDER_BIKE,
            ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION,
            ServiceTasks.MIRAVELO_SEND_REJECTION,
        )
        assertThat(Messages.entries).containsExactly(
            Messages.MIRAVELO_ADDRESS_CHANGED,
            Messages.MIRAVELO_APPLICATION_WITHDRAWN,
            Messages.MIRAVELO_CONTRACT_SIGNED,
            Messages.MIRAVELO_HANDOVER_REPORTED,
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED,
        )
        assertThat(Errors.entries).containsExactly(Errors.MIRAVELO_APPLICATION_INVALID)
        assertThat(Escalations.entries).containsExactly(Escalations.MIRAVELO_CONTRACT_NOT_SIGNED)
    }

    private fun kindOf(node: FlowNodes.Node): String = when (node) {
        BoundaryApplicationInvalid,
        BoundaryCompensateContract,
        BoundaryCompensateInsurance,
        BoundaryCompensateOrder,
        BoundaryContractNotSigned,
        TimerSignatureReminder,
        -> "boundary"

        EndEventApplicationCancelled,
        EndEventApplicationRejected,
        EndEventContractConcluded,
        EndEventContractNotSigned,
        EndEventCustomerReminded,
        EndEventDeliveryAddressUpdated,
        EndEventLeasingActive,
        EventContractSigned,
        EventReverseApplication,
        StartEventAddressChanged,
        StartEventApplicationWithdrawn,
        StartEventCustomerEligible,
        StartEventLeasingRequestReceived,
        TimerSignatureDeadline,
        TimerWithdrawalPeriodElapsed,
        -> "event"

        GatewayAwaitSignature,
        GatewayCollectRejections,
        GatewayFork,
        GatewayIsSolvent,
        GatewayJoin,
        -> "gateway"

        BusinessRuleTaskCheckCreditRating,
        CallActivityCancelBikeOrder,
        ReceiveTaskHandoverReported,
        ServiceTaskCancelContract,
        ServiceTaskCancelPolicy,
        ServiceTaskIssueInsurancePolicy,
        ServiceTaskOrderBike,
        ServiceTaskSendCancellationConfirmation,
        ServiceTaskSendContract,
        ServiceTaskSendRejection,
        ServiceTaskSendReminderMail,
        ServiceTaskValidateApplication,
        SubProcessAddressChanged,
        SubProcessApplicationWithdrawn,
        SubProcessConcludeContract,
        UserTaskUpdateDeliveryAddress,
        -> "activity"
    }

    private fun kindOf(elementType: BpmnElementType): String = when {
        elementType == BpmnElementType.BOUNDARY_EVENT -> "boundary"
        elementType.name.endsWith("GATEWAY") -> "gateway"
        elementType.name.endsWith("EVENT") -> "event"
        else -> "activity"
    }
}
