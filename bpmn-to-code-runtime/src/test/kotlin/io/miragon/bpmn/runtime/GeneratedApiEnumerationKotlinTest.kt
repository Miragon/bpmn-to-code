package io.miragon.bpmn.runtime

import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BoundaryApplicationInvalid
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BoundaryCompensateContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BoundaryCompensateInsurance
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BoundaryCompensateOrder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BoundaryContractNotSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.BusinessRuleTaskCheckCreditRating
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.CallActivityCancelBikeOrder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventApplicationCancelled
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventApplicationRejected
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventContractConcluded
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventContractNotSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventCustomerReminded
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventDeliveryAddressUpdated
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EndEventLeasingActive
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EventContractSigned
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.EventReverseApplication
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.GatewayAwaitSignature
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.GatewayCollectRejections
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.GatewayFork
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.GatewayIsSolvent
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.GatewayJoin
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ReceiveTaskHandoverReported
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskCancelContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskCancelPolicy
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskIssueInsurancePolicy
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskOrderBike
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskSendCancellationConfirmation
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskSendContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskSendRejection
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskSendReminderMail
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.ServiceTaskValidateApplication
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.StartEventAddressChanged
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.StartEventApplicationWithdrawn
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.StartEventCustomerEligible
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.StartEventLeasingRequestReceived
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.SubProcessAddressChanged
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.SubProcessApplicationWithdrawn
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.SubProcessConcludeContract
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.TimerSignatureDeadline
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.TimerSignatureReminder
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.TimerWithdrawalPeriodElapsed
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.Flow.UserTaskUpdateDeliveryAddress
import io.miragon.bpmn.runtime.path.example.Errors
import io.miragon.bpmn.runtime.path.example.Escalations
import io.miragon.bpmn.runtime.path.example.Messages
import io.miragon.bpmn.runtime.path.example.ServiceTasks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Enumerates the generated bike-leasing API from **Kotlin**: `Flow.entries`, the sealed `Flow.Node` and the
 * shared definitions' `entries`. The `GeneratedApiEnumerationJavaTest` sibling covers the Java `all()` accessors.
 */
class GeneratedApiEnumerationKotlinTest {

    @Test
    fun `flow entries list exactly the nodes of the process`() {
        val everyNode = Flow.Node::class.sealedSubclasses.map { it.objectInstance }

        assertThat(Flow.entries).hasSize(42).containsExactlyInAnyOrderElementsOf(everyNode)
    }

    @Test
    fun `a when over the sealed node type is exhaustive without an else branch`() {
        assertThat(Flow.entries).allSatisfy { node ->
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
        assertThat(Errors.entries).containsExactly(Errors.MIRAVELO_APPLICATION_INVALID_APPLICATION_INVALID)
        assertThat(Escalations.entries).containsExactly(Escalations.MIRAVELO_CONTRACT_NOT_SIGNED_CONTRACT_NOT_SIGNED)
    }

    private fun kindOf(node: Flow.Node): String = when (node) {
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
