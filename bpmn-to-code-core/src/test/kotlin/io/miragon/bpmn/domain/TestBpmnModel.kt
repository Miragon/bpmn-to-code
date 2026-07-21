package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.EventDefinitionType
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.ErrorDefinition
import io.miragon.bpmn.domain.shared.CompensationDefinition
import io.miragon.bpmn.domain.shared.CompensationType
import io.miragon.bpmn.domain.shared.EscalationDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.ASYNC_AFTER_KEY
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.ASYNC_BEFORE_KEY
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.EXCLUSIVE_KEY
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDefinition
import io.miragon.bpmn.domain.shared.MessageDirection
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition.Companion.IMPL_VALUE_KEY
import io.miragon.bpmn.domain.shared.SignalDefinition
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

fun testBpmnModel(
    processId: String = "order",
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = listOf(FlowNodeDefinition(id = "create-order")),
    sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
    messages: List<MessageDefinition> = listOf(MessageDefinition(id = "messageId", name = "messageName")),
    signals: List<SignalDefinition> = listOf(SignalDefinition(id = "signalId", name = "signalName")),
    errors: List<ErrorDefinition> = listOf(ErrorDefinition(id = "errorId", name = "errorName", code = "errorCode")),
    escalations: List<EscalationDefinition> = emptyList(),
    compensations: List<CompensationDefinition> = emptyList(),
    detectedEngine: ProcessEngine? = null,
) = BpmnModel(
    processId = processId,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = signals,
    errors = errors,
    escalations = escalations,
    compensations = compensations,
    detectedEngine = detectedEngine,
)

fun testBpmnModelApi(
    model: ProcessModel = testBpmnModel(),
    packagePath: String = "packagePath",
    language: OutputLanguage = OutputLanguage.KOTLIN,
    engine: ProcessEngine = ProcessEngine.ZEEBE,
) = BpmnModelApi(
    model = model,
    packagePath = packagePath,
    outputLanguage = language,
    engine = engine,
)

fun testSubscribeNewsletterBpmnModel(
    processId: String = "newsletterSubscription",
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = listOf(
        FlowNodeDefinition("CallActivity_AbortRegistration", BpmnNodeType.Activity.CallActivity,
            displayName = "Abort registration",
            properties = FlowNodeProperties.CallActivity(CallActivityDefinition("CallActivity_AbortRegistration", "abort-registration")),
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
            previousElements = listOf("Timer_After3Days"), followingElements = listOf("CompensationEndEvent_RegistrationAborted")),
        FlowNodeDefinition("Activity_ConfirmRegistration", BpmnNodeType.Activity.Task(TaskKind.USER),
            displayName = "Confirm subscription",
            attachedElements = listOf("Timer_EveryDay"),
            parentId = "SubProcess_Confirmation",
            previousElements = listOf("Activity_SendConfirmationMail"), followingElements = listOf("EndEvent_SubscriptionConfirmed")),
        FlowNodeDefinition("Activity_SendConfirmationMail", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            displayName = "Send confirmation mail",
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("Activity_SendConfirmationMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendConfirmationMail"))),
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
            parentId = "SubProcess_Confirmation",
            previousElements = listOf("StartEvent_RequestReceived", "Timer_EveryDay"), followingElements = listOf("Activity_ConfirmRegistration")),
        FlowNodeDefinition("Activity_SendWelcomeMail", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            displayName = "Send Welcome-Mail",
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("Activity_SendWelcomeMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendWelcomeMail"))),
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
            previousElements = listOf("SubProcess_Confirmation"), followingElements = listOf("EndEvent_RegistrationCompleted"),
            engineSpecificProperties = mapOf(ASYNC_BEFORE_KEY to true, ASYNC_AFTER_KEY to true, EXCLUSIVE_KEY to false)),
        FlowNodeDefinition("CompensationEndEvent_RegistrationAborted", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.COMPENSATION),
            displayName = "Registration aborted",
            previousElements = listOf("CallActivity_AbortRegistration")),
        FlowNodeDefinition("CompensationEvent_OnSubscriptionCounter", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.COMPENSATION),
            displayName = "Registration aborted",
            attachedToRef = "serviceTask_incrementSubscriptionCounter"),
        FlowNodeDefinition("CompensationTask_DecrementSubscriptionCounter", BpmnNodeType.Activity.Task(TaskKind.NONE),
            displayName = "Decrement subscription counter"),
        FlowNodeDefinition("EndEvent_RegistrationCompleted", BpmnNodeType.Event(EventShape.END_EVENT),
            displayName = "Registration completed",
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("EndEvent_RegistrationCompleted", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.registrationCompleted"))),
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
            previousElements = listOf("Activity_SendWelcomeMail")),
        FlowNodeDefinition("EndEvent_RegistrationNotPossible", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.SIGNAL),
            displayName = "Registration not possible",
            previousElements = listOf("ErrorEvent_InvalidMail")),
        FlowNodeDefinition("EndEvent_SubscriptionConfirmed", BpmnNodeType.Event(EventShape.END_EVENT),
            displayName = "Subscription confirmed",
            parentId = "SubProcess_Confirmation",
            previousElements = listOf("Activity_ConfirmRegistration")),
        FlowNodeDefinition("ErrorEvent_InvalidMail", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.ERROR),
            displayName = "Invalid Mail",
            attachedToRef = "SubProcess_Confirmation",
            followingElements = listOf("EndEvent_RegistrationNotPossible")),
        FlowNodeDefinition("serviceTask_incrementSubscriptionCounter", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            displayName = "Increment subscription counter",
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_incrementSubscriptionCounter", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "counterClass"))),
            attachedElements = listOf("CompensationEvent_OnSubscriptionCounter"),
            previousElements = listOf("StartEvent_SubmitRegistrationForm"), followingElements = listOf("SubProcess_Confirmation")),
        FlowNodeDefinition("StartEvent_RequestReceived", BpmnNodeType.Event(EventShape.START_EVENT),
            displayName = "Subscription requested",
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
            parentId = "SubProcess_Confirmation",
            followingElements = listOf("Activity_SendConfirmationMail")),
        FlowNodeDefinition("StartEvent_SubmitRegistrationForm", BpmnNodeType.Event(EventShape.START_EVENT, EventDefinitionType.MESSAGE),
            displayName = "Submit newsletter form",
            properties = FlowNodeProperties.MessageEvent("Message_FormSubmitted", MessageDirection.CATCH),
            variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
            followingElements = listOf("serviceTask_incrementSubscriptionCounter")),
        FlowNodeDefinition("SubProcess_Confirmation", BpmnNodeType.Activity.SubProcess(SubProcessKind.PLAIN),
            displayName = "Subscription Confirmation",
            attachedElements = listOf("ErrorEvent_InvalidMail", "Timer_After3Days"),
            previousElements = listOf("serviceTask_incrementSubscriptionCounter"), followingElements = listOf("Activity_SendWelcomeMail")),
        FlowNodeDefinition("Timer_After3Days", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
            displayName = "After 3 days",
            properties = FlowNodeProperties.Timer(TimerDefinition("Timer_After3Days", "Duration", "$" + "{testVariable}")),
            attachedToRef = "SubProcess_Confirmation",
            followingElements = listOf("CallActivity_AbortRegistration")),
        FlowNodeDefinition("Timer_EveryDay", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
            displayName = "Every day",
            properties = FlowNodeProperties.Timer(TimerDefinition("Timer_EveryDay", "Duration", "PT1M")),
            attachedToRef = "Activity_ConfirmRegistration",
            parentId = "SubProcess_Confirmation",
            followingElements = listOf("Activity_SendConfirmationMail")),
    ),
    sequenceFlows: List<SequenceFlowDefinition> = listOf(
        SequenceFlowDefinition("Flow_05i3x1y", "StartEvent_RequestReceived", "Activity_SendConfirmationMail"),
        SequenceFlowDefinition("Flow_09cuvzp", "SubProcess_Confirmation", "Activity_SendWelcomeMail"),
        SequenceFlowDefinition("Flow_0i2ctuv", "ErrorEvent_InvalidMail", "EndEvent_RegistrationNotPossible"),
        SequenceFlowDefinition("Flow_0x4ewvb", "Timer_EveryDay", "Activity_SendConfirmationMail"),
        SequenceFlowDefinition("Flow_0zdmt0t", "serviceTask_incrementSubscriptionCounter", "SubProcess_Confirmation"),
        SequenceFlowDefinition("Flow_1bckm43", "Activity_SendConfirmationMail", "Activity_ConfirmRegistration"),
        SequenceFlowDefinition("Flow_1bsb8no", "CallActivity_AbortRegistration", "CompensationEndEvent_RegistrationAborted"),
        SequenceFlowDefinition("Flow_1cpwe57", "Activity_ConfirmRegistration", "EndEvent_SubscriptionConfirmed"),
        SequenceFlowDefinition("Flow_1csfyyz", "StartEvent_SubmitRegistrationForm", "serviceTask_incrementSubscriptionCounter"),
        SequenceFlowDefinition("Flow_1i7hjid", "Activity_SendWelcomeMail", "EndEvent_RegistrationCompleted"),
        SequenceFlowDefinition("Flow_1l1lj4m", "Timer_After3Days", "CallActivity_AbortRegistration"),
    ),
    messages: List<MessageDefinition> = listOf(
        MessageDefinition("StartEvent_SubmitRegistrationForm", "Message_FormSubmitted"),
    ),
    signals: List<SignalDefinition> = listOf(
        SignalDefinition("EndEvent_RegistrationNotPossible", "Signal_RegistrationNotPossible")
    ),
    errors: List<ErrorDefinition> = listOf(
        ErrorDefinition("ErrorEvent_InvalidMail", "Error_InvalidMail", "500")
    ),
    escalations: List<EscalationDefinition> = emptyList(),
    compensations: List<CompensationDefinition> = listOf(
        CompensationDefinition("CompensationEndEvent_RegistrationAborted", CompensationType.THROWING),
        CompensationDefinition("CompensationEvent_OnSubscriptionCounter", CompensationType.CATCHING),
    ),
    detectedEngine: ProcessEngine? = null,
) = testBpmnModel(
    processId = processId,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = signals,
    errors = errors,
    escalations = escalations,
    compensations = compensations,
    detectedEngine = detectedEngine,
)

fun testSendNewsletterBpmnModel(
    processId: String = "sendNewsletter",
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = listOf(
        // Main flow
        FlowNodeDefinition("startEvent_editionCreated", BpmnNodeType.Event(EventShape.START_EVENT),
            followingElements = listOf("serviceTask_loadSubscribers")),
        FlowNodeDefinition("serviceTask_loadSubscribers", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_loadSubscribers", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.loadSubscribers"))),
            variables = listOf(VariableDefinition("subscribers", VariableDirection.OUTPUT), VariableDefinition("author", VariableDirection.OUTPUT)),
            previousElements = listOf("startEvent_editionCreated"), followingElements = listOf("gateway_hasSubscribers")),
        FlowNodeDefinition("gateway_hasSubscribers", BpmnNodeType.Gateway(GatewayKind.EXCLUSIVE),
            previousElements = listOf("serviceTask_loadSubscribers"), followingElements = listOf("serviceTask_sendToSubscriber", "endEvent_noSubscribers")),
        FlowNodeDefinition("serviceTask_sendToSubscriber", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_sendToSubscriber", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendMailToSubscriber"))),
            previousElements = listOf("gateway_hasSubscribers"), followingElements = listOf("serviceTask_notifyAuthor")),
        FlowNodeDefinition("serviceTask_notifyAuthor", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_notifyAuthor", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.notifyAuthor"))),
            previousElements = listOf("serviceTask_sendToSubscriber"), followingElements = listOf("endEvent_editionSent")),
        FlowNodeDefinition("endEvent_editionSent", BpmnNodeType.Event(EventShape.END_EVENT),
            previousElements = listOf("serviceTask_notifyAuthor")),
        FlowNodeDefinition("endEvent_noSubscribers", BpmnNodeType.Event(EventShape.END_EVENT),
            previousElements = listOf("gateway_hasSubscribers")),
        // Event subprocess: error handling
        FlowNodeDefinition("eventSubProcess_errorHandling", BpmnNodeType.Activity.SubProcess(SubProcessKind.PLAIN)),
        FlowNodeDefinition("event_mailRejected", BpmnNodeType.Event(EventShape.START_EVENT, EventDefinitionType.MESSAGE),
            properties = FlowNodeProperties.MessageEvent("Message_MailRejected", MessageDirection.CATCH),
            parentId = "eventSubProcess_errorHandling",
            followingElements = listOf("serviceTask_analyzeError")),
        FlowNodeDefinition("serviceTask_analyzeError", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_analyzeError", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.analyzeSendError"))),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("event_mailRejected"), followingElements = listOf("gateway_canSendAgain")),
        FlowNodeDefinition("gateway_canSendAgain", BpmnNodeType.Gateway(GatewayKind.EXCLUSIVE),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("serviceTask_analyzeError"), followingElements = listOf("serviceTask_sendMailAgain", "escalationEndEvent_nofitySupport")),
        FlowNodeDefinition("serviceTask_sendMailAgain", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
            properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_sendMailAgain", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendMailToSubscriber"))),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("gateway_canSendAgain"), followingElements = listOf("eventGateway_afterSendingAgain")),
        FlowNodeDefinition("eventGateway_afterSendingAgain", BpmnNodeType.Gateway(GatewayKind.EVENT_BASED),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("serviceTask_sendMailAgain"), followingElements = listOf("timer_noRejectionForOneDay", "event_mailRejectedAgain")),
        FlowNodeDefinition("timer_noRejectionForOneDay", BpmnNodeType.Event(EventShape.INTERMEDIATE_CATCH_EVENT, EventDefinitionType.TIMER),
            properties = FlowNodeProperties.Timer(TimerDefinition("timer_noRejectionForOneDay", "Duration", "PT1D")),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("eventGateway_afterSendingAgain"), followingElements = listOf("endEvent_issueResolved")),
        FlowNodeDefinition("escalationEndEvent_nofitySupport", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.ESCALATION),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("gateway_canSendAgain")),
        FlowNodeDefinition("event_mailRejectedAgain", BpmnNodeType.Event(EventShape.INTERMEDIATE_CATCH_EVENT, EventDefinitionType.MESSAGE),
            properties = FlowNodeProperties.MessageEvent("Message_MailRejectedAgain", MessageDirection.CATCH),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("eventGateway_afterSendingAgain"), followingElements = listOf("escalationEndEvent_nofitySupportAfterRepeatedError")),
        FlowNodeDefinition("escalationEndEvent_nofitySupportAfterRepeatedError", BpmnNodeType.Event(EventShape.END_EVENT),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("event_mailRejectedAgain")),
        FlowNodeDefinition("endEvent_issueResolved", BpmnNodeType.Event(EventShape.END_EVENT),
            parentId = "eventSubProcess_errorHandling",
            previousElements = listOf("timer_noRejectionForOneDay")),
    ),
    sequenceFlows: List<SequenceFlowDefinition> = listOf(
        // Main flow
        SequenceFlowDefinition("Flow_0bianz5", "startEvent_editionCreated", "serviceTask_loadSubscribers"),
        SequenceFlowDefinition("Flow_04andb8", "serviceTask_loadSubscribers", "gateway_hasSubscribers"),
        SequenceFlowDefinition("Flow_1jogut0", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
        SequenceFlowDefinition("Flow_1gsz7wd", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}"),
        SequenceFlowDefinition("Flow_1ruayvl", "serviceTask_sendToSubscriber", "serviceTask_notifyAuthor"),
        SequenceFlowDefinition("Flow_0v2v55n", "serviceTask_notifyAuthor", "endEvent_editionSent"),
        // Event subprocess
        SequenceFlowDefinition("Flow_0vtppnk", "event_mailRejected", "serviceTask_analyzeError"),
        SequenceFlowDefinition("Flow_13nmnag", "serviceTask_analyzeError", "gateway_canSendAgain"),
        SequenceFlowDefinition("Flow_1izucof", "gateway_canSendAgain", "serviceTask_sendMailAgain", flowName = "Yes", isDefault = true),
        SequenceFlowDefinition("Flow_18nf2jh", "gateway_canSendAgain", "escalationEndEvent_nofitySupport", flowName = "No", conditionExpression = "\${rejection.reason == \"PERMANENT\"}"),
        SequenceFlowDefinition("Flow_0vym6nu", "serviceTask_sendMailAgain", "eventGateway_afterSendingAgain"),
        SequenceFlowDefinition("Flow_0enjkoe", "eventGateway_afterSendingAgain", "timer_noRejectionForOneDay"),
        SequenceFlowDefinition("Flow_081cykl", "eventGateway_afterSendingAgain", "event_mailRejectedAgain"),
        SequenceFlowDefinition("Flow_0x9thpq", "event_mailRejectedAgain", "escalationEndEvent_nofitySupportAfterRepeatedError"),
        SequenceFlowDefinition("Flow_0338xzf", "timer_noRejectionForOneDay", "endEvent_issueResolved"),
    ),
    messages: List<MessageDefinition> = listOf(
        MessageDefinition("event_mailRejected", "Message_MailRejected"),
        MessageDefinition("event_mailRejectedAgain", "Message_MailRejectedAgain"),
    ),
    signals: List<SignalDefinition> = emptyList(),
    errors: List<ErrorDefinition> = emptyList(),
    escalations: List<EscalationDefinition> = listOf(
        EscalationDefinition("escalationEndEvent_nofitySupport", "escalation_notifySupport", "200"),
    ),
    compensations: List<CompensationDefinition> = emptyList(),
) = testBpmnModel(
    processId = processId,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = signals,
    errors = errors,
    escalations = escalations,
    compensations = compensations,
)
