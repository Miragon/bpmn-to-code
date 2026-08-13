package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

fun testProcessModel(
    processId: String = "order",
    processName: String? = null,
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = listOf(FlowNodeDefinition.Unknown(id = "create-order")),
    sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
    messages: List<RootElementDefinition.Message> = listOf(RootElementDefinition.Message(id = "messageId", name = "messageName")),
    signals: List<RootElementDefinition.Signal> = listOf(RootElementDefinition.Signal(id = "signalId", name = "signalName")),
    errors: List<RootElementDefinition.Error> = listOf(RootElementDefinition.Error(id = "errorId", name = "errorName", code = "errorCode")),
    escalations: List<RootElementDefinition.Escalation> = emptyList(),
    detectedEngine: ProcessEngine? = null,
    variants: List<ProcessModel.Variant> = emptyList(),
) = ProcessModel(
    processId = processId,
    processName = processName,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    definitions = RootElements(messages, signals, errors, escalations),
    detectedEngine = detectedEngine,
    variants = variants,
)

fun testProcessModelApi(
    model: ProcessModel = testProcessModel(),
    packagePath: String = "packagePath",
    language: OutputLanguage = OutputLanguage.KOTLIN,
    engine: ProcessEngine = ProcessEngine.ZEEBE,
) = BpmnModelApi(
    model = model,
    packagePath = packagePath,
    outputLanguage = language,
    targetEngine = engine,
)

/**
 * Polymorphic copy of a [FlowNodeDefinition] with a new [id], across the sealed hierarchy.
 */
fun FlowNodeDefinition.withId(id: String?): FlowNodeDefinition = when (this) {
    is FlowNodeDefinition.Gateway -> copy(id = id)
    is FlowNodeDefinition.Event -> copy(id = id)
    is FlowNodeDefinition.Activity.Task -> copy(id = id)
    is FlowNodeDefinition.Activity.SubProcess -> copy(id = id)
    is FlowNodeDefinition.Activity.CallActivity -> copy(id = id)
    is FlowNodeDefinition.Unknown -> copy(id = id)
}

/**
 * Polymorphic copy of a [FlowNodeDefinition] with a new [displayName], across the sealed hierarchy.
 */
fun FlowNodeDefinition.withDisplayName(displayName: String?): FlowNodeDefinition = when (this) {
    is FlowNodeDefinition.Gateway -> copy(displayName = displayName)
    is FlowNodeDefinition.Event -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.Task -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.SubProcess -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.CallActivity -> copy(displayName = displayName)
    is FlowNodeDefinition.Unknown -> copy(displayName = displayName)
}

/**
 * Convenience builder for a service task backed by a Zeebe job worker.
 */
fun jobWorkerTask(
    id: String,
    jobType: String,
    displayName: String? = null,
    incoming: List<String> = emptyList(),
    outgoing: List<String> = emptyList(),
    variables: List<VariableDefinition> = emptyList(),
    boundaryEventRefs: List<String> = emptyList(),
    engineAttributes: Map<String, Any?> = emptyMap(),
) = FlowNodeDefinition.Activity.Task(
    id = id,
    kind = TaskKind.SERVICE,
    displayName = displayName,
    incoming = incoming,
    outgoing = outgoing,
    implementation = TaskImplementation.JobWorker(jobType),
    boundaryEventRefs = boundaryEventRefs,
    variables = variables,
    engineAttributes = engineAttributes,
)

private val asyncAttributes = mapOf(
    "camunda:asyncBefore" to true,
    "camunda:asyncAfter" to true,
    "camunda:exclusive" to false,
)

@Suppress("LongParameterList")
fun testSubscribeNewsletterModel(
    processId: String = "newsletterSubscription",
    processName: String? = null,
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = subscribeNewsletterFlowNodes(),
    sequenceFlows: List<SequenceFlowDefinition> = subscribeNewsletterSequenceFlows(),
    messages: List<RootElementDefinition.Message> = listOf(
        RootElementDefinition.Message("Message_FormSubmitted", "Message_FormSubmitted"),
    ),
    signals: List<RootElementDefinition.Signal> = listOf(
        RootElementDefinition.Signal("Signal_RegistrationNotPossible", "Signal_RegistrationNotPossible"),
    ),
    errors: List<RootElementDefinition.Error> = listOf(
        RootElementDefinition.Error("Error_InvalidMail", "Error_InvalidMail", "500"),
    ),
    escalations: List<RootElementDefinition.Escalation> = emptyList(),
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
 * Root-scope nodes of the newsletter-subscription process; the confirmation sub-process owns its own.
 */
fun subscribeNewsletterFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Activity.CallActivity(
        id = "CallActivity_AbortRegistration",
        definition = CallActivityDefinition("CallActivity_AbortRegistration", "abort-registration"),
        displayName = "Abort registration",
        incoming = listOf("Flow_1l1lj4m"),
        outgoing = listOf("Flow_1bsb8no"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
    ),
    jobWorkerTask(
        id = "Activity_SendWelcomeMail",
        jobType = "newsletter.sendWelcomeMail",
        displayName = "Send Welcome-Mail",
        incoming = listOf("Flow_16hub0n"),
        outgoing = listOf("Flow_1i7hjid"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
        engineAttributes = asyncAttributes,
    ),
    jobWorkerTask(
        id = "Activity_NotifyCommunity",
        jobType = "newsletter.notifyCommunity",
        displayName = "Notify community",
        incoming = listOf("Flow_1p5t47z"),
        outgoing = listOf("Flow_1duwy83"),
        engineAttributes = asyncAttributes,
    ),
    FlowNodeDefinition.Gateway(
        id = "Gateway_SplitNotifications",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("Flow_09cuvzp"),
        outgoing = listOf("Flow_16hub0n", "Flow_1p5t47z"),
    ),
    FlowNodeDefinition.Gateway(
        id = "Gateway_JoinNotifications",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("Flow_1i7hjid", "Flow_1duwy83"),
        outgoing = listOf("Flow_1862jd8"),
    ),
    FlowNodeDefinition.Event(
        id = "CompensationEndEvent_RegistrationAborted",
        shape = EventShape.END_EVENT,
        displayName = "Registration aborted",
        incoming = listOf("Flow_1bsb8no"),
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Event(
        id = "CompensationEvent_OnSubscriptionCounter",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Registration aborted",
        attachedToRef = "serviceTask_incrementSubscriptionCounter",
        interrupting = true,
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "CompensationTask_DecrementSubscriptionCounter",
        kind = TaskKind.NONE,
        displayName = "Decrement subscription counter",
    ),
    FlowNodeDefinition.Event(
        id = "EndEvent_RegistrationCompleted",
        shape = EventShape.END_EVENT,
        displayName = "Registration completed",
        incoming = listOf("Flow_1862jd8"),
        implementation = TaskImplementation.JobWorker("newsletter.registrationCompleted"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "EndEvent_RegistrationNotPossible",
        shape = EventShape.END_EVENT,
        displayName = "Registration not possible",
        incoming = listOf("Flow_0i2ctuv"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Signal("Signal_RegistrationNotPossible", "Signal_RegistrationNotPossible"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "ErrorEvent_InvalidMail",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Invalid Mail",
        attachedToRef = "SubProcess_Confirmation",
        interrupting = true,
        outgoing = listOf("Flow_0i2ctuv"),
        eventDefinitions = listOf(EventDefinitionInstance.Error("Error_InvalidMail", "Error_InvalidMail", "500")),
    ),
    jobWorkerTask(
        id = "serviceTask_incrementSubscriptionCounter",
        jobType = "counterClass",
        displayName = "Increment subscription counter",
        incoming = listOf("Flow_1csfyyz"),
        outgoing = listOf("Flow_0zdmt0t"),
        boundaryEventRefs = listOf("CompensationEvent_OnSubscriptionCounter"),
    ),
    FlowNodeDefinition.Event(
        id = "StartEvent_SubmitRegistrationForm",
        shape = EventShape.START_EVENT,
        displayName = "Submit newsletter form",
        outgoing = listOf("Flow_1csfyyz"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_FormSubmitted", "Message_FormSubmitted")),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "SubProcess_Confirmation",
        kind = SubProcessKind.PLAIN,
        displayName = "Subscription Confirmation",
        incoming = listOf("Flow_0zdmt0t"),
        outgoing = listOf("Flow_09cuvzp"),
        boundaryEventRefs = listOf("ErrorEvent_InvalidMail", "Timer_After3Days"),
        flowNodes = confirmationSubProcessNodes(),
        sequenceFlows = confirmationSubProcessFlows(),
    ),
    FlowNodeDefinition.Event(
        id = "Timer_After3Days",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "After 3 days",
        attachedToRef = "SubProcess_Confirmation",
        interrupting = true,
        outgoing = listOf("Flow_1l1lj4m"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "$" + "{testVariable}")),
    ),
)

private fun confirmationSubProcessNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Activity.Task(
        id = "Activity_ConfirmRegistration",
        kind = TaskKind.USER,
        displayName = "Confirm subscription",
        incoming = listOf("Flow_1bckm43"),
        outgoing = listOf("Flow_1cpwe57"),
        boundaryEventRefs = listOf("Timer_EveryDay"),
    ),
    jobWorkerTask(
        id = "Activity_SendConfirmationMail",
        jobType = "newsletter.sendConfirmationMail",
        displayName = "Send confirmation mail",
        incoming = listOf("Flow_05i3x1y", "Flow_0x4ewvb"),
        outgoing = listOf("Flow_1bckm43"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "EndEvent_SubscriptionConfirmed",
        shape = EventShape.END_EVENT,
        displayName = "Subscription confirmed",
        incoming = listOf("Flow_1cpwe57"),
    ),
    FlowNodeDefinition.Event(
        id = "StartEvent_RequestReceived",
        shape = EventShape.START_EVENT,
        displayName = "Subscription requested",
        outgoing = listOf("Flow_05i3x1y"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "Timer_EveryDay",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Every day",
        attachedToRef = "Activity_ConfirmRegistration",
        interrupting = false,
        outgoing = listOf("Flow_0x4ewvb"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
    ),
)

private fun confirmationSubProcessFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("Flow_05i3x1y", "StartEvent_RequestReceived", "Activity_SendConfirmationMail"),
    SequenceFlowDefinition("Flow_0x4ewvb", "Timer_EveryDay", "Activity_SendConfirmationMail"),
    SequenceFlowDefinition("Flow_1bckm43", "Activity_SendConfirmationMail", "Activity_ConfirmRegistration"),
    SequenceFlowDefinition("Flow_1cpwe57", "Activity_ConfirmRegistration", "EndEvent_SubscriptionConfirmed"),
)

fun subscribeNewsletterSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("Flow_09cuvzp", "SubProcess_Confirmation", "Gateway_SplitNotifications"),
    SequenceFlowDefinition("Flow_0i2ctuv", "ErrorEvent_InvalidMail", "EndEvent_RegistrationNotPossible"),
    SequenceFlowDefinition("Flow_0zdmt0t", "serviceTask_incrementSubscriptionCounter", "SubProcess_Confirmation"),
    SequenceFlowDefinition("Flow_16hub0n", "Gateway_SplitNotifications", "Activity_SendWelcomeMail"),
    SequenceFlowDefinition("Flow_1862jd8", "Gateway_JoinNotifications", "EndEvent_RegistrationCompleted"),
    SequenceFlowDefinition("Flow_1bsb8no", "CallActivity_AbortRegistration", "CompensationEndEvent_RegistrationAborted"),
    SequenceFlowDefinition("Flow_1csfyyz", "StartEvent_SubmitRegistrationForm", "serviceTask_incrementSubscriptionCounter"),
    SequenceFlowDefinition("Flow_1duwy83", "Activity_NotifyCommunity", "Gateway_JoinNotifications"),
    SequenceFlowDefinition("Flow_1i7hjid", "Activity_SendWelcomeMail", "Gateway_JoinNotifications"),
    SequenceFlowDefinition("Flow_1l1lj4m", "Timer_After3Days", "CallActivity_AbortRegistration"),
    SequenceFlowDefinition("Flow_1p5t47z", "Gateway_SplitNotifications", "Activity_NotifyCommunity"),
)

@Suppress("LongParameterList")
fun testSendNewsletterModel(
    processId: String = "sendNewsletter",
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = sendNewsletterFlowNodes(),
    sequenceFlows: List<SequenceFlowDefinition> = sendNewsletterSequenceFlows(),
    messages: List<RootElementDefinition.Message> = listOf(
        RootElementDefinition.Message("Message_MailRejected", "Message_MailRejected"),
        RootElementDefinition.Message("Message_MailRejectedAgain", "Message_MailRejectedAgain"),
    ),
    signals: List<RootElementDefinition.Signal> = emptyList(),
    errors: List<RootElementDefinition.Error> = emptyList(),
    escalations: List<RootElementDefinition.Escalation> = listOf(
        RootElementDefinition.Escalation("escalation_notifySupport", "escalation_notifySupport", "200"),
    ),
) = testProcessModel(
    processId = processId,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    messages = messages,
    signals = signals,
    errors = errors,
    escalations = escalations,
)

fun sendNewsletterFlowNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "startEvent_editionCreated",
        shape = EventShape.START_EVENT,
        outgoing = listOf("Flow_0bianz5"),
    ),
    jobWorkerTask(
        id = "serviceTask_loadSubscribers",
        jobType = "newsletter.loadSubscribers",
        incoming = listOf("Flow_0bianz5"),
        outgoing = listOf("Flow_04andb8"),
        variables = listOf(
            VariableDefinition("subscribers", VariableDirection.OUTPUT),
            VariableDefinition("author", VariableDirection.OUTPUT),
        ),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_hasSubscribers",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("Flow_04andb8"),
        outgoing = listOf("Flow_1jogut0", "Flow_1gsz7wd"),
        defaultFlow = "Flow_1jogut0",
    ),
    jobWorkerTask(
        id = "serviceTask_sendToSubscriber",
        jobType = "newsletter.sendMailToSubscriber",
        incoming = listOf("Flow_1jogut0"),
        outgoing = listOf("Flow_1ruayvl"),
    ),
    jobWorkerTask(
        id = "serviceTask_notifyAuthor",
        jobType = "newsletter.notifyAuthor",
        incoming = listOf("Flow_1ruayvl"),
        outgoing = listOf("Flow_0v2v55n"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_editionSent",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_0v2v55n"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_noSubscribers",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_1gsz7wd"),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "eventSubProcess_errorHandling",
        kind = SubProcessKind.EVENT,
        flowNodes = errorHandlingNodes(),
        sequenceFlows = errorHandlingFlows(),
    ),
)

private fun errorHandlingNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Event(
        id = "event_mailRejected",
        shape = EventShape.START_EVENT,
        interrupting = true,
        outgoing = listOf("Flow_0vtppnk"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_MailRejected", "Message_MailRejected")),
        ),
    ),
    jobWorkerTask(
        id = "serviceTask_analyzeError",
        jobType = "newsletter.analyzeSendError",
        incoming = listOf("Flow_0vtppnk"),
        outgoing = listOf("Flow_13nmnag"),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_canSendAgain",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("Flow_13nmnag"),
        outgoing = listOf("Flow_1izucof", "Flow_18nf2jh"),
        defaultFlow = "Flow_1izucof",
    ),
    jobWorkerTask(
        id = "serviceTask_sendMailAgain",
        jobType = "newsletter.sendMailToSubscriber",
        incoming = listOf("Flow_1izucof"),
        outgoing = listOf("Flow_0vym6nu"),
    ),
    FlowNodeDefinition.Gateway(
        id = "eventGateway_afterSendingAgain",
        kind = GatewayKind.EVENT_BASED,
        incoming = listOf("Flow_0vym6nu"),
        outgoing = listOf("Flow_0enjkoe", "Flow_081cykl"),
    ),
    FlowNodeDefinition.Event(
        id = "timer_noRejectionForOneDay",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        incoming = listOf("Flow_0enjkoe"),
        outgoing = listOf("Flow_0338xzf"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1D")),
    ),
    FlowNodeDefinition.Event(
        id = "escalationEndEvent_nofitySupport",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_18nf2jh"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Escalation("escalation_notifySupport", "escalation_notifySupport", "200"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "event_mailRejectedAgain",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        incoming = listOf("Flow_081cykl"),
        outgoing = listOf("Flow_0x9thpq"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_MailRejectedAgain", "Message_MailRejectedAgain")),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "escalationEndEvent_nofitySupportAfterRepeatedError",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_0x9thpq"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_issueResolved",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_0338xzf"),
    ),
)

private fun errorHandlingFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("Flow_0vtppnk", "event_mailRejected", "serviceTask_analyzeError"),
    SequenceFlowDefinition("Flow_13nmnag", "serviceTask_analyzeError", "gateway_canSendAgain"),
    SequenceFlowDefinition("Flow_1izucof", "gateway_canSendAgain", "serviceTask_sendMailAgain", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition("Flow_18nf2jh", "gateway_canSendAgain", "escalationEndEvent_nofitySupport", flowName = "No", conditionExpression = "\${rejection.reason == \"PERMANENT\"}"),
    SequenceFlowDefinition("Flow_0vym6nu", "serviceTask_sendMailAgain", "eventGateway_afterSendingAgain"),
    SequenceFlowDefinition("Flow_0enjkoe", "eventGateway_afterSendingAgain", "timer_noRejectionForOneDay"),
    SequenceFlowDefinition("Flow_081cykl", "eventGateway_afterSendingAgain", "event_mailRejectedAgain"),
    SequenceFlowDefinition("Flow_0x9thpq", "event_mailRejectedAgain", "escalationEndEvent_nofitySupportAfterRepeatedError"),
    SequenceFlowDefinition("Flow_0338xzf", "timer_noRejectionForOneDay", "endEvent_issueResolved"),
)

fun sendNewsletterSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("Flow_0bianz5", "startEvent_editionCreated", "serviceTask_loadSubscribers"),
    SequenceFlowDefinition("Flow_04andb8", "serviceTask_loadSubscribers", "gateway_hasSubscribers"),
    SequenceFlowDefinition("Flow_1jogut0", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition("Flow_1gsz7wd", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}"),
    SequenceFlowDefinition("Flow_1ruayvl", "serviceTask_sendToSubscriber", "serviceTask_notifyAuthor"),
    SequenceFlowDefinition("Flow_0v2v55n", "serviceTask_notifyAuthor", "endEvent_editionSent"),
)
