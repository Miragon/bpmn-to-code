package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

/**
 * In-memory mirror of `shared/bpmn/c7/bike-leasing.bpmn`, so codegen and JSON tests don't depend on the extractor.
 */
@Suppress("LongParameterList")
fun testBikeLeasingModel(
    processId: String = "bikeLeasing",
    processName: String? = null,
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = bikeLeasingFlowNodes(),
    sequenceFlows: List<SequenceFlowDefinition> = bikeLeasingSequenceFlows(),
    messages: List<RootElementDefinition.Message> = listOf(
        RootElementDefinition.Message("message_leasingRequestReceived", "miravelo.leasingRequestReceived"),
        RootElementDefinition.Message("message_contractSigned", "miravelo.contractSigned"),
        RootElementDefinition.Message("message_handoverReported", "miravelo.handoverReported"),
        RootElementDefinition.Message("message_applicationWithdrawn", "miravelo.applicationWithdrawn"),
        RootElementDefinition.Message("message_addressChanged", "miravelo.addressChanged"),
    ),
    signals: List<RootElementDefinition.Signal> = emptyList(),
    errors: List<RootElementDefinition.Error> = listOf(
        RootElementDefinition.Error(
            id = "error_applicationInvalid",
            name = "miravelo.applicationInvalid",
            code = "applicationInvalid",
        ),
    ),
    escalations: List<RootElementDefinition.Escalation> = listOf(
        RootElementDefinition.Escalation(
            id = "escalation_contractNotSigned",
            name = "miravelo.contractNotSigned",
            code = "contractNotSigned",
        ),
    ),
    detectedEngine: ProcessEngine? = null,
) = testProcessModel(
    processId = processId,
    processName = processName,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = signals,
    errors = errors,
    escalations = escalations,
    detectedEngine = detectedEngine,
)

/**
 * In-memory mirror of `shared/bpmn/c7/cancel-bike-order.bpmn`.
 */
@Suppress("LongParameterList")
fun testCancelBikeOrderModel(
    processId: String = "cancelBikeOrder",
    processName: String? = null,
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = cancelBikeOrderFlowNodes(),
    sequenceFlows: List<SequenceFlowDefinition> = cancelBikeOrderSequenceFlows(),
    messages: List<RootElementDefinition.Message> = listOf(
        RootElementDefinition.Message("message_bikeOrderCancelled", "miravelo.bikeOrderCancelled"),
    ),
    detectedEngine: ProcessEngine? = null,
) = testProcessModel(
    processId = processId,
    processName = processName,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = emptyList(),
    errors = emptyList(),
    escalations = emptyList(),
    detectedEngine = detectedEngine,
)

@Suppress("LongMethod")
fun bikeLeasingFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_leasingRequestReceived",
        shape = EventShape.START_EVENT,
        displayName = "Leasing request received",
        outgoing = listOf("flow_leasingRequestReceivedToValidateApplication"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("message_leasingRequestReceived", "miravelo.leasingRequestReceived")),
        ),
        variables = listOf(
            VariableDefinition("applicationId", VariableDirection.INPUT),
            VariableDefinition("bikeIds", VariableDirection.INPUT),
            VariableDefinition("monthlyNetIncome", VariableDirection.INPUT),
            VariableDefinition("age", VariableDirection.INPUT),
        ),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_validateApplication",
        kind = TaskKind.SERVICE,
        displayName = "Validate application",
        incoming = listOf("flow_leasingRequestReceivedToValidateApplication"),
        outgoing = listOf("flow_validateApplicationToCheckCreditRating"),
        implementation = TaskImplementation.DelegateExpression("\${validateApplicationDelegate}"),
        boundaryEventRefs = listOf("boundary_applicationInvalid"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "businessRuleTask_checkCreditRating",
        kind = TaskKind.BUSINESS_RULE,
        displayName = "Check credit rating",
        incoming = listOf("flow_validateApplicationToCheckCreditRating"),
        outgoing = listOf("flow_checkCreditRatingToIsSolvent"),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_isSolvent",
        kind = GatewayKind.EXCLUSIVE,
        displayName = "Solvent?",
        incoming = listOf("flow_checkCreditRatingToIsSolvent"),
        outgoing = listOf("flow_isSolventToConcludeContract", "flow_isSolventToCollectRejections"),
        defaultFlow = "flow_isSolventToConcludeContract",
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "subProcess_concludeContract",
        kind = SubProcessKind.PLAIN,
        displayName = "Conclude contract",
        incoming = listOf("flow_isSolventToConcludeContract"),
        outgoing = listOf("flow_concludeContractToFork"),
        flowNodes = concludeContractFlowNodes(),
        sequenceFlows = concludeContractSequenceFlows(),
        boundaryEventRefs = listOf("boundary_compensateContract", "boundary_contractNotSigned", "timer_signatureReminder"),
    ),
    FlowNodeDefinition.Event(
        id = "boundary_compensateContract",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Contract to revoke",
        eventDefinitions = listOf(EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false)),
        attachedToRef = "subProcess_concludeContract",
        interrupting = true,
    ),
    FlowNodeDefinition.Event(
        id = "boundary_contractNotSigned",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Contract not signed",
        outgoing = listOf("flow_contractNotSignedToCollectRejections"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Escalation(escalationRef = "escalation_contractNotSigned", escalationName = "miravelo.contractNotSigned", escalationCode = "contractNotSigned"),
        ),
        attachedToRef = "subProcess_concludeContract",
        interrupting = true,
    ),
    FlowNodeDefinition.Event(
        id = "timer_signatureReminder",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "7 days passed",
        outgoing = listOf("flow_signatureReminderToSendReminderMail"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "P7D")),
        attachedToRef = "subProcess_concludeContract",
        interrupting = false,
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_sendReminderMail",
        kind = TaskKind.SERVICE,
        displayName = "Send reminder mail",
        incoming = listOf("flow_signatureReminderToSendReminderMail"),
        outgoing = listOf("flow_sendReminderMailToCustomerReminded"),
        implementation = TaskImplementation.Expression("\${mailService.sendReminder(applicationId)}"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_customerReminded",
        shape = EventShape.END_EVENT,
        displayName = "Customer reminded",
        incoming = listOf("flow_sendReminderMailToCustomerReminded"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_cancelContract",
        kind = TaskKind.SERVICE,
        displayName = "Cancel contract",
        implementation = TaskImplementation.DelegateExpression("\${cancelContractDelegate}"),
        isForCompensation = true,
    ),
    FlowNodeDefinition.Event(
        id = "boundary_applicationInvalid",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Application invalid",
        outgoing = listOf("flow_applicationInvalidToCollectRejections"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Error(errorRef = "error_applicationInvalid", errorName = "miravelo.applicationInvalid", errorCode = "applicationInvalid"),
        ),
        attachedToRef = "serviceTask_validateApplication",
        interrupting = true,
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_collectRejections",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("flow_isSolventToCollectRejections", "flow_applicationInvalidToCollectRejections", "flow_contractNotSignedToCollectRejections"),
        outgoing = listOf("flow_collectRejectionsToSendRejection"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_sendRejection",
        kind = TaskKind.SERVICE,
        displayName = "Send rejection",
        incoming = listOf("flow_collectRejectionsToSendRejection"),
        outgoing = listOf("flow_sendRejectionToApplicationRejected"),
        implementation = TaskImplementation.ExternalTask("miravelo.sendRejection"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_applicationRejected",
        shape = EventShape.END_EVENT,
        displayName = "Application rejected",
        incoming = listOf("flow_sendRejectionToApplicationRejected"),
        eventDefinitions = listOf(EventDefinitionInstance.Terminate),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_fork",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("flow_concludeContractToFork"),
        outgoing = listOf("flow_forkToOrderBike", "flow_forkToIssueInsurancePolicy"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_orderBike",
        kind = TaskKind.SERVICE,
        displayName = "Order bike",
        incoming = listOf("flow_forkToOrderBike"),
        outgoing = listOf("flow_orderBikeToJoin"),
        implementation = TaskImplementation.ExternalTask("miravelo.orderBike"),
        multiInstance = MultiInstanceDefinition(sequential = false, inputCollection = "\${bikeIds}", inputElement = "bikeId"),
        boundaryEventRefs = listOf("boundary_compensateOrder"),
        variables = listOf(
            VariableDefinition(name = "bikeIds", direction = VariableDirection.INPUT, valueExpression = "\${bikeIds}"),
            VariableDefinition(name = "bikeId", direction = VariableDirection.INPUT, valueExpression = "bikeId"),
        ),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_issueInsurancePolicy",
        kind = TaskKind.SERVICE,
        displayName = "Issue insurance policy",
        incoming = listOf("flow_forkToIssueInsurancePolicy"),
        outgoing = listOf("flow_issueInsurancePolicyToJoin"),
        implementation = TaskImplementation.JavaClass("io.miravelo.leasing.IssueInsurancePolicyDelegate"),
        multiInstance = MultiInstanceDefinition(sequential = true, inputCollection = "\${bikeIds}", inputElement = "bikeId"),
        boundaryEventRefs = listOf("boundary_compensateInsurance"),
        variables = listOf(
            VariableDefinition(name = "bikeIds", direction = VariableDirection.INPUT, valueExpression = "\${bikeIds}"),
            VariableDefinition(name = "bikeId", direction = VariableDirection.INPUT, valueExpression = "bikeId"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "boundary_compensateOrder",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Order to cancel",
        eventDefinitions = listOf(EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false)),
        attachedToRef = "serviceTask_orderBike",
        interrupting = true,
    ),
    FlowNodeDefinition.Event(
        id = "boundary_compensateInsurance",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Policy to cancel",
        eventDefinitions = listOf(EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false)),
        attachedToRef = "serviceTask_issueInsurancePolicy",
        interrupting = true,
    ),
    FlowNodeDefinition.Activity.CallActivity(
        id = "callActivity_cancelBikeOrder",
        definition = CallActivityDefinition(
            id = "callActivity_cancelBikeOrder",
            calledElement = "cancelBikeOrder",
            mappings = listOf(
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = "orderIds", target = "orderIds"),
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, sourceExpression = "\${applicationId}", target = "applicationId"),
                CallActivityDefinition.Mapping(direction = VariableDirection.OUTPUT, source = "cancellationCosts", target = "cancellationCosts"),
            ),
        ),
        displayName = "Cancel bike order",
        isForCompensation = true,
        variables = listOf(
            VariableDefinition(name = "orderIds", direction = VariableDirection.INPUT, valueExpression = "orderIds"),
            VariableDefinition(
                name = "applicationId",
                direction = VariableDirection.INPUT,
                valueExpression = "\${applicationId}",
            ),
            VariableDefinition(
                name = "cancellationCosts",
                direction = VariableDirection.OUTPUT,
                valueExpression = "cancellationCosts",
            ),
        ),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_cancelPolicy",
        kind = TaskKind.SERVICE,
        displayName = "Cancel policy",
        implementation = TaskImplementation.ExternalTask("miravelo.cancelPolicy"),
        isForCompensation = true,
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_join",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("flow_orderBikeToJoin", "flow_issueInsurancePolicyToJoin"),
        outgoing = listOf("flow_joinToHandoverReported"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "receiveTask_handoverReported",
        kind = TaskKind.RECEIVE,
        displayName = "Await bike handover",
        incoming = listOf("flow_joinToHandoverReported"),
        outgoing = listOf("flow_handoverReportedToWithdrawalPeriodElapsed"),
        message = MessageReference("message_handoverReported", "miravelo.handoverReported"),
    ),
    FlowNodeDefinition.Event(
        id = "timer_withdrawalPeriodElapsed",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        displayName = "Withdrawal period elapsed",
        incoming = listOf("flow_handoverReportedToWithdrawalPeriodElapsed"),
        outgoing = listOf("flow_withdrawalPeriodElapsedToLeasingActive"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "\${withdrawalPeriod}")),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_leasingActive",
        shape = EventShape.END_EVENT,
        displayName = "Leasing active",
        incoming = listOf("flow_withdrawalPeriodElapsedToLeasingActive"),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "subProcess_applicationWithdrawn",
        kind = SubProcessKind.EVENT,
        displayName = "Application withdrawn",
        flowNodes = applicationWithdrawnFlowNodes(),
        sequenceFlows = applicationWithdrawnSequenceFlows(),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "subProcess_addressChanged",
        kind = SubProcessKind.EVENT,
        displayName = "Delivery address changed",
        flowNodes = addressChangedFlowNodes(),
        sequenceFlows = addressChangedSequenceFlows(),
    ),
)

fun bikeLeasingSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition(id = "flow_leasingRequestReceivedToValidateApplication", sourceRef = "startEvent_leasingRequestReceived", targetRef = "serviceTask_validateApplication"),
    SequenceFlowDefinition(id = "flow_validateApplicationToCheckCreditRating", sourceRef = "serviceTask_validateApplication", targetRef = "businessRuleTask_checkCreditRating"),
    SequenceFlowDefinition(id = "flow_checkCreditRatingToIsSolvent", sourceRef = "businessRuleTask_checkCreditRating", targetRef = "gateway_isSolvent"),
    SequenceFlowDefinition(id = "flow_isSolventToConcludeContract", sourceRef = "gateway_isSolvent", targetRef = "subProcess_concludeContract", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition(id = "flow_isSolventToCollectRejections", sourceRef = "gateway_isSolvent", targetRef = "gateway_collectRejections", flowName = "No", conditionExpression = "\${!solvent}"),
    SequenceFlowDefinition(id = "flow_applicationInvalidToCollectRejections", sourceRef = "boundary_applicationInvalid", targetRef = "gateway_collectRejections"),
    SequenceFlowDefinition(id = "flow_contractNotSignedToCollectRejections", sourceRef = "boundary_contractNotSigned", targetRef = "gateway_collectRejections"),
    SequenceFlowDefinition(id = "flow_collectRejectionsToSendRejection", sourceRef = "gateway_collectRejections", targetRef = "serviceTask_sendRejection"),
    SequenceFlowDefinition(id = "flow_sendRejectionToApplicationRejected", sourceRef = "serviceTask_sendRejection", targetRef = "endEvent_applicationRejected"),
    SequenceFlowDefinition(id = "flow_signatureReminderToSendReminderMail", sourceRef = "timer_signatureReminder", targetRef = "serviceTask_sendReminderMail"),
    SequenceFlowDefinition(id = "flow_sendReminderMailToCustomerReminded", sourceRef = "serviceTask_sendReminderMail", targetRef = "endEvent_customerReminded"),
    SequenceFlowDefinition(id = "flow_concludeContractToFork", sourceRef = "subProcess_concludeContract", targetRef = "gateway_fork"),
    SequenceFlowDefinition(id = "flow_forkToOrderBike", sourceRef = "gateway_fork", targetRef = "serviceTask_orderBike"),
    SequenceFlowDefinition(id = "flow_forkToIssueInsurancePolicy", sourceRef = "gateway_fork", targetRef = "serviceTask_issueInsurancePolicy"),
    SequenceFlowDefinition(id = "flow_orderBikeToJoin", sourceRef = "serviceTask_orderBike", targetRef = "gateway_join"),
    SequenceFlowDefinition(id = "flow_issueInsurancePolicyToJoin", sourceRef = "serviceTask_issueInsurancePolicy", targetRef = "gateway_join"),
    SequenceFlowDefinition(id = "flow_joinToHandoverReported", sourceRef = "gateway_join", targetRef = "receiveTask_handoverReported"),
    SequenceFlowDefinition(id = "flow_handoverReportedToWithdrawalPeriodElapsed", sourceRef = "receiveTask_handoverReported", targetRef = "timer_withdrawalPeriodElapsed"),
    SequenceFlowDefinition(id = "flow_withdrawalPeriodElapsedToLeasingActive", sourceRef = "timer_withdrawalPeriodElapsed", targetRef = "endEvent_leasingActive"),
)

private fun concludeContractFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_customerEligible",
        shape = EventShape.START_EVENT,
        displayName = "Customer eligible",
        outgoing = listOf("flow_customerEligibleToSendContract"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_sendContract",
        kind = TaskKind.SERVICE,
        displayName = "Send contract",
        incoming = listOf("flow_customerEligibleToSendContract"),
        outgoing = listOf("flow_sendContractToAwaitSignature"),
        implementation = TaskImplementation.DelegateExpression("\${sendContractDelegate}"),
        ioMapping = IoMapping(
            inputs = listOf(IoMapping.Parameter(target = "applicationId", source = "\${applicationId}")),
            outputs = listOf(IoMapping.Parameter(target = "contractId", source = "\${contractId}")),
        ),
        variables = listOf(
            VariableDefinition(
                name = "applicationId",
                direction = VariableDirection.INPUT,
                valueExpression = "\${applicationId}",
            ),
            VariableDefinition(
                name = "contractId",
                direction = VariableDirection.OUTPUT,
                valueExpression = "\${contractId}",
            ),
        ),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_awaitSignature",
        kind = GatewayKind.EVENT_BASED,
        displayName = "Await signature",
        incoming = listOf("flow_sendContractToAwaitSignature"),
        outgoing = listOf("flow_awaitSignatureToContractSigned", "flow_awaitSignatureToSignatureDeadline"),
    ),
    FlowNodeDefinition.Event(
        id = "event_contractSigned",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        displayName = "Contract signed",
        incoming = listOf("flow_awaitSignatureToContractSigned"),
        outgoing = listOf("flow_contractSignedToContractConcluded"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("message_contractSigned", "miravelo.contractSigned")),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_contractConcluded",
        shape = EventShape.END_EVENT,
        displayName = "Contract concluded",
        incoming = listOf("flow_contractSignedToContractConcluded"),
    ),
    FlowNodeDefinition.Event(
        id = "timer_signatureDeadline",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        displayName = "14 days passed",
        incoming = listOf("flow_awaitSignatureToSignatureDeadline"),
        outgoing = listOf("flow_signatureDeadlineToContractNotSigned"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "P14D")),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_contractNotSigned",
        shape = EventShape.END_EVENT,
        displayName = "Contract not signed",
        incoming = listOf("flow_signatureDeadlineToContractNotSigned"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Escalation(escalationRef = "escalation_contractNotSigned", escalationName = "miravelo.contractNotSigned", escalationCode = "contractNotSigned"),
        ),
    ),
)

private fun concludeContractSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition(id = "flow_customerEligibleToSendContract", sourceRef = "startEvent_customerEligible", targetRef = "serviceTask_sendContract"),
    SequenceFlowDefinition(id = "flow_sendContractToAwaitSignature", sourceRef = "serviceTask_sendContract", targetRef = "gateway_awaitSignature"),
    SequenceFlowDefinition(id = "flow_awaitSignatureToContractSigned", sourceRef = "gateway_awaitSignature", targetRef = "event_contractSigned"),
    SequenceFlowDefinition(id = "flow_contractSignedToContractConcluded", sourceRef = "event_contractSigned", targetRef = "endEvent_contractConcluded"),
    SequenceFlowDefinition(id = "flow_awaitSignatureToSignatureDeadline", sourceRef = "gateway_awaitSignature", targetRef = "timer_signatureDeadline"),
    SequenceFlowDefinition(id = "flow_signatureDeadlineToContractNotSigned", sourceRef = "timer_signatureDeadline", targetRef = "endEvent_contractNotSigned"),
)

private fun applicationWithdrawnFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_applicationWithdrawn",
        shape = EventShape.START_EVENT,
        displayName = "Application withdrawn",
        outgoing = listOf("flow_applicationWithdrawnToReverseApplication"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("message_applicationWithdrawn", "miravelo.applicationWithdrawn")),
        ),
        interrupting = true,
    ),
    FlowNodeDefinition.Event(
        id = "event_reverseApplication",
        shape = EventShape.INTERMEDIATE_THROW_EVENT,
        displayName = "Application reversed",
        incoming = listOf("flow_applicationWithdrawnToReverseApplication"),
        outgoing = listOf("flow_reverseApplicationToSendCancellationConfirmation"),
        eventDefinitions = listOf(EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false)),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_sendCancellationConfirmation",
        kind = TaskKind.SERVICE,
        displayName = "Send cancellation confirmation",
        incoming = listOf("flow_reverseApplicationToSendCancellationConfirmation"),
        outgoing = listOf("flow_sendCancellationConfirmationToApplicationCancelled"),
        implementation = TaskImplementation.ExternalTask("miravelo.sendCancellationConfirmation"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_applicationCancelled",
        shape = EventShape.END_EVENT,
        displayName = "Application cancelled",
        incoming = listOf("flow_sendCancellationConfirmationToApplicationCancelled"),
    ),
)

private fun applicationWithdrawnSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition(id = "flow_applicationWithdrawnToReverseApplication", sourceRef = "startEvent_applicationWithdrawn", targetRef = "event_reverseApplication"),
    SequenceFlowDefinition(id = "flow_reverseApplicationToSendCancellationConfirmation", sourceRef = "event_reverseApplication", targetRef = "serviceTask_sendCancellationConfirmation"),
    SequenceFlowDefinition(id = "flow_sendCancellationConfirmationToApplicationCancelled", sourceRef = "serviceTask_sendCancellationConfirmation", targetRef = "endEvent_applicationCancelled"),
)

private fun addressChangedFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_addressChanged",
        shape = EventShape.START_EVENT,
        displayName = "Address changed",
        outgoing = listOf("flow_addressChangedToUpdateDeliveryAddress"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("message_addressChanged", "miravelo.addressChanged")),
        ),
        interrupting = false,
        variables = listOf(
            VariableDefinition("street", VariableDirection.INPUT),
            VariableDefinition("city", VariableDirection.INPUT),
        ),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "userTask_updateDeliveryAddress",
        kind = TaskKind.USER,
        displayName = "Update delivery address",
        incoming = listOf("flow_addressChangedToUpdateDeliveryAddress"),
        outgoing = listOf("flow_updateDeliveryAddressToDeliveryAddressUpdated"),
        ioMapping = IoMapping(
            inputs = listOf(IoMapping.Parameter(target = "deliveryAddress", source = "\${deliveryAddress}")),
            outputs = listOf(IoMapping.Parameter(target = "deliveryAddress", source = "\${deliveryAddress}")),
        ),
        variables = listOf(
            VariableDefinition(
                name = "deliveryAddress",
                direction = VariableDirection.INPUT,
                valueExpression = "\${deliveryAddress}",
            ),
            VariableDefinition(
                name = "deliveryAddress",
                direction = VariableDirection.OUTPUT,
                valueExpression = "\${deliveryAddress}",
            ),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_deliveryAddressUpdated",
        shape = EventShape.END_EVENT,
        displayName = "Delivery address updated",
        incoming = listOf("flow_updateDeliveryAddressToDeliveryAddressUpdated"),
    ),
)

private fun addressChangedSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition(id = "flow_addressChangedToUpdateDeliveryAddress", sourceRef = "startEvent_addressChanged", targetRef = "userTask_updateDeliveryAddress"),
    SequenceFlowDefinition(id = "flow_updateDeliveryAddressToDeliveryAddressUpdated", sourceRef = "userTask_updateDeliveryAddress", targetRef = "endEvent_deliveryAddressUpdated"),
)

fun cancelBikeOrderFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_cancellationRequired",
        shape = EventShape.START_EVENT,
        displayName = "Cancellation required",
        outgoing = listOf("flow_cancellationRequiredToRequestCancellation"),
        variables = listOf(VariableDefinition("orderIds", VariableDirection.INPUT)),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_requestCancellation",
        kind = TaskKind.SERVICE,
        displayName = "Request cancellation",
        incoming = listOf("flow_cancellationRequiredToRequestCancellation"),
        outgoing = listOf("flow_requestCancellationToCancellationPossible"),
        implementation = TaskImplementation.ExternalTask("miravelo.requestCancellation"),
        ioMapping = IoMapping(
            outputs = listOf(IoMapping.Parameter(target = "cancellationPossible", source = "\${cancellationPossible}")),
        ),
        boundaryEventRefs = listOf("boundary_cancellationFailed"),
        variables = listOf(
            VariableDefinition(
                name = "cancellationPossible",
                direction = VariableDirection.OUTPUT,
                valueExpression = "\${cancellationPossible}",
            ),
        ),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_cancellationPossible",
        kind = GatewayKind.EXCLUSIVE,
        displayName = "Cancellation possible?",
        incoming = listOf("flow_requestCancellationToCancellationPossible"),
        outgoing = listOf("flow_cancellationPossibleToMergeReturn", "flow_cancellationNotPossibleToCollectClarifications"),
        defaultFlow = "flow_cancellationPossibleToMergeReturn",
    ),
    FlowNodeDefinition.Event(
        id = "boundary_cancellationFailed",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Cancellation failed",
        outgoing = listOf("flow_cancellationFailedToCollectClarifications"),
        eventDefinitions = listOf(EventDefinitionInstance.Error(errorRef = null, errorName = null, errorCode = null)),
        attachedToRef = "serviceTask_requestCancellation",
        interrupting = true,
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_collectClarifications",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("flow_cancellationNotPossibleToCollectClarifications", "flow_cancellationFailedToCollectClarifications"),
        outgoing = listOf("flow_collectClarificationsToClarifyReturn"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "userTask_clarifyReturn",
        kind = TaskKind.USER,
        displayName = "Clarify return",
        incoming = listOf("flow_collectClarificationsToClarifyReturn"),
        outgoing = listOf("flow_clarifyReturnToMergeReturn"),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_mergeReturn",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("flow_cancellationPossibleToMergeReturn", "flow_clarifyReturnToMergeReturn"),
        outgoing = listOf("flow_mergeReturnToBookCancellationCosts"),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_bookCancellationCosts",
        kind = TaskKind.SERVICE,
        displayName = "Book cancellation costs",
        incoming = listOf("flow_mergeReturnToBookCancellationCosts"),
        outgoing = listOf("flow_bookCancellationCostsToBikeOrderCancelled"),
        implementation = TaskImplementation.DelegateExpression("\${bookCancellationCostsDelegate}"),
        ioMapping = IoMapping(
            outputs = listOf(IoMapping.Parameter(target = "cancellationCosts", source = "\${cancellationCosts}")),
        ),
        variables = listOf(
            VariableDefinition(
                name = "cancellationCosts",
                direction = VariableDirection.OUTPUT,
                valueExpression = "\${cancellationCosts}",
            ),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_bikeOrderCancelled",
        shape = EventShape.END_EVENT,
        displayName = "Bike order cancelled",
        incoming = listOf("flow_bookCancellationCostsToBikeOrderCancelled"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("message_bikeOrderCancelled", "miravelo.bikeOrderCancelled")),
        ),
        implementation = TaskImplementation.ExternalTask("miravelo.bikeOrderCancelled"),
    ),
)

fun cancelBikeOrderSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition(id = "flow_cancellationRequiredToRequestCancellation", sourceRef = "startEvent_cancellationRequired", targetRef = "serviceTask_requestCancellation"),
    SequenceFlowDefinition(id = "flow_requestCancellationToCancellationPossible", sourceRef = "serviceTask_requestCancellation", targetRef = "gateway_cancellationPossible"),
    SequenceFlowDefinition(id = "flow_cancellationPossibleToMergeReturn", sourceRef = "gateway_cancellationPossible", targetRef = "gateway_mergeReturn", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition(id = "flow_cancellationNotPossibleToCollectClarifications", sourceRef = "gateway_cancellationPossible", targetRef = "gateway_collectClarifications", flowName = "No", conditionExpression = "\${!cancellationPossible}"),
    SequenceFlowDefinition(id = "flow_cancellationFailedToCollectClarifications", sourceRef = "boundary_cancellationFailed", targetRef = "gateway_collectClarifications"),
    SequenceFlowDefinition(id = "flow_collectClarificationsToClarifyReturn", sourceRef = "gateway_collectClarifications", targetRef = "userTask_clarifyReturn"),
    SequenceFlowDefinition(id = "flow_clarifyReturnToMergeReturn", sourceRef = "userTask_clarifyReturn", targetRef = "gateway_mergeReturn"),
    SequenceFlowDefinition(id = "flow_mergeReturnToBookCancellationCosts", sourceRef = "gateway_mergeReturn", targetRef = "serviceTask_bookCancellationCosts"),
    SequenceFlowDefinition(id = "flow_bookCancellationCostsToBikeOrderCancelled", sourceRef = "serviceTask_bookCancellationCosts", targetRef = "endEvent_bikeOrderCancelled"),
)
