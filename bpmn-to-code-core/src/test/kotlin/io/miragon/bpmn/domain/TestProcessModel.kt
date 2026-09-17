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
        id = "callActivity_abortRegistration",
        definition = CallActivityDefinition("callActivity_abortRegistration", "abort-registration"),
        displayName = "Abort registration",
        incoming = listOf("flow_after3DaysToAbort"),
        outgoing = listOf("flow_abortToRegistrationAborted"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
    ),
    jobWorkerTask(
        id = "serviceTask_sendWelcomeMail",
        jobType = "newsletter.sendWelcomeMail",
        displayName = "Send Welcome-Mail",
        incoming = listOf("flow_splitToWelcomeMail"),
        outgoing = listOf("flow_welcomeMailToJoin"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
        engineAttributes = asyncAttributes,
    ),
    jobWorkerTask(
        id = "serviceTask_notifyCommunity",
        jobType = "newsletter.notifyCommunity",
        displayName = "Notify community",
        incoming = listOf("flow_splitToNotifyCommunity"),
        outgoing = listOf("flow_notifyCommunityToJoin"),
        engineAttributes = asyncAttributes,
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_splitNotifications",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("flow_confirmationToSplit"),
        outgoing = listOf("flow_splitToWelcomeMail", "flow_splitToNotifyCommunity"),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_joinNotifications",
        kind = GatewayKind.PARALLEL,
        incoming = listOf("flow_welcomeMailToJoin", "flow_notifyCommunityToJoin"),
        outgoing = listOf("flow_joinToRegistrationCompleted"),
    ),
    FlowNodeDefinition.Event(
        id = "compensationEndEvent_registrationAborted",
        shape = EventShape.END_EVENT,
        displayName = "Registration aborted",
        incoming = listOf("flow_abortToRegistrationAborted"),
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Event(
        id = "compensationEvent_onSubscriptionCounter",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Registration aborted",
        attachedToRef = "serviceTask_incrementSubscriptionCounter",
        interrupting = true,
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Activity.Task(
        id = "serviceTask_decrementSubscriptionCounter",
        kind = TaskKind.NONE,
        displayName = "Decrement subscription counter",
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_registrationCompleted",
        shape = EventShape.END_EVENT,
        displayName = "Registration completed",
        incoming = listOf("flow_joinToRegistrationCompleted"),
        implementation = TaskImplementation.JobWorker("newsletter.registrationCompleted"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_registrationNotPossible",
        shape = EventShape.END_EVENT,
        displayName = "Registration not possible",
        incoming = listOf("flow_invalidMailToNotPossible"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Signal("Signal_RegistrationNotPossible", "Signal_RegistrationNotPossible"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "errorEvent_invalidMail",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Invalid Mail",
        attachedToRef = "subProcess_confirmation",
        interrupting = true,
        outgoing = listOf("flow_invalidMailToNotPossible"),
        eventDefinitions = listOf(EventDefinitionInstance.Error("Error_InvalidMail", "Error_InvalidMail", "500")),
    ),
    jobWorkerTask(
        id = "serviceTask_incrementSubscriptionCounter",
        jobType = "counterClass",
        displayName = "Increment subscription counter",
        incoming = listOf("flow_submitToIncrementCounter"),
        outgoing = listOf("flow_incrementCounterToConfirmation"),
        boundaryEventRefs = listOf("compensationEvent_onSubscriptionCounter"),
    ),
    FlowNodeDefinition.Event(
        id = "startEvent_submitRegistrationForm",
        shape = EventShape.START_EVENT,
        displayName = "Submit newsletter form",
        outgoing = listOf("flow_submitToIncrementCounter"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_FormSubmitted", "Message_FormSubmitted")),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "subProcess_confirmation",
        kind = SubProcessKind.PLAIN,
        displayName = "Subscription Confirmation",
        incoming = listOf("flow_incrementCounterToConfirmation"),
        outgoing = listOf("flow_confirmationToSplit"),
        boundaryEventRefs = listOf("errorEvent_invalidMail", "timer_after3Days"),
        flowNodes = confirmationSubProcessNodes(),
        sequenceFlows = confirmationSubProcessFlows(),
    ),
    FlowNodeDefinition.Event(
        id = "timer_after3Days",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "After 3 days",
        attachedToRef = "subProcess_confirmation",
        interrupting = true,
        outgoing = listOf("flow_after3DaysToAbort"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "$" + "{testVariable}")),
    ),
)

private fun confirmationSubProcessNodes(): List<FlowNodeDefinition> = listOf(
    FlowNodeDefinition.Activity.Task(
        id = "userTask_confirmRegistration",
        kind = TaskKind.USER,
        displayName = "Confirm subscription",
        incoming = listOf("flow_confirmationMailToConfirm"),
        outgoing = listOf("flow_confirmToConfirmed"),
        boundaryEventRefs = listOf("timer_everyDay"),
    ),
    jobWorkerTask(
        id = "serviceTask_sendConfirmationMail",
        jobType = "newsletter.sendConfirmationMail",
        displayName = "Send confirmation mail",
        incoming = listOf("flow_requestToConfirmationMail", "flow_everyDayToConfirmationMail"),
        outgoing = listOf("flow_confirmationMailToConfirm"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_subscriptionConfirmed",
        shape = EventShape.END_EVENT,
        displayName = "Subscription confirmed",
        incoming = listOf("flow_confirmToConfirmed"),
    ),
    FlowNodeDefinition.Event(
        id = "startEvent_requestReceived",
        shape = EventShape.START_EVENT,
        displayName = "Subscription requested",
        outgoing = listOf("flow_requestToConfirmationMail"),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "timer_everyDay",
        shape = EventShape.BOUNDARY_EVENT,
        displayName = "Every day",
        attachedToRef = "userTask_confirmRegistration",
        interrupting = false,
        outgoing = listOf("flow_everyDayToConfirmationMail"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
    ),
)

private fun confirmationSubProcessFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("flow_requestToConfirmationMail", "startEvent_requestReceived", "serviceTask_sendConfirmationMail"),
    SequenceFlowDefinition("flow_everyDayToConfirmationMail", "timer_everyDay", "serviceTask_sendConfirmationMail"),
    SequenceFlowDefinition("flow_confirmationMailToConfirm", "serviceTask_sendConfirmationMail", "userTask_confirmRegistration"),
    SequenceFlowDefinition("flow_confirmToConfirmed", "userTask_confirmRegistration", "endEvent_subscriptionConfirmed"),
)

fun subscribeNewsletterSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("flow_confirmationToSplit", "subProcess_confirmation", "gateway_splitNotifications"),
    SequenceFlowDefinition("flow_invalidMailToNotPossible", "errorEvent_invalidMail", "endEvent_registrationNotPossible"),
    SequenceFlowDefinition("flow_incrementCounterToConfirmation", "serviceTask_incrementSubscriptionCounter", "subProcess_confirmation"),
    SequenceFlowDefinition("flow_splitToWelcomeMail", "gateway_splitNotifications", "serviceTask_sendWelcomeMail"),
    SequenceFlowDefinition("flow_joinToRegistrationCompleted", "gateway_joinNotifications", "endEvent_registrationCompleted"),
    SequenceFlowDefinition("flow_abortToRegistrationAborted", "callActivity_abortRegistration", "compensationEndEvent_registrationAborted"),
    SequenceFlowDefinition("flow_submitToIncrementCounter", "startEvent_submitRegistrationForm", "serviceTask_incrementSubscriptionCounter"),
    SequenceFlowDefinition("flow_notifyCommunityToJoin", "serviceTask_notifyCommunity", "gateway_joinNotifications"),
    SequenceFlowDefinition("flow_welcomeMailToJoin", "serviceTask_sendWelcomeMail", "gateway_joinNotifications"),
    SequenceFlowDefinition("flow_after3DaysToAbort", "timer_after3Days", "callActivity_abortRegistration"),
    SequenceFlowDefinition("flow_splitToNotifyCommunity", "gateway_splitNotifications", "serviceTask_notifyCommunity"),
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
        outgoing = listOf("flow_editionToLoadSubscribers"),
    ),
    jobWorkerTask(
        id = "serviceTask_loadSubscribers",
        jobType = "newsletter.loadSubscribers",
        incoming = listOf("flow_editionToLoadSubscribers"),
        outgoing = listOf("flow_loadSubscribersToGateway"),
        variables = listOf(
            VariableDefinition("subscribers", VariableDirection.OUTPUT),
            VariableDefinition("author", VariableDirection.OUTPUT),
        ),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_hasSubscribers",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("flow_loadSubscribersToGateway"),
        outgoing = listOf("flow_hasSubscribers", "flow_noSubscribers"),
        defaultFlow = "flow_hasSubscribers",
    ),
    jobWorkerTask(
        id = "serviceTask_sendToSubscriber",
        jobType = "newsletter.sendMailToSubscriber",
        incoming = listOf("flow_hasSubscribers"),
        outgoing = listOf("flow_sendToNotifyAuthor"),
    ),
    jobWorkerTask(
        id = "serviceTask_notifyAuthor",
        jobType = "newsletter.notifyAuthor",
        incoming = listOf("flow_sendToNotifyAuthor"),
        outgoing = listOf("flow_notifyAuthorToEditionSent"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_editionSent",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_notifyAuthorToEditionSent"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_noSubscribers",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_noSubscribers"),
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
        outgoing = listOf("flow_mailRejectedToAnalyze"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_MailRejected", "Message_MailRejected")),
        ),
    ),
    jobWorkerTask(
        id = "serviceTask_analyzeError",
        jobType = "newsletter.analyzeSendError",
        incoming = listOf("flow_mailRejectedToAnalyze"),
        outgoing = listOf("flow_analyzeToCanSendAgain"),
    ),
    FlowNodeDefinition.Gateway(
        id = "gateway_canSendAgain",
        kind = GatewayKind.EXCLUSIVE,
        incoming = listOf("flow_analyzeToCanSendAgain"),
        outgoing = listOf("flow_canSendAgain", "flow_cannotSendAgain"),
        defaultFlow = "flow_canSendAgain",
    ),
    jobWorkerTask(
        id = "serviceTask_sendMailAgain",
        jobType = "newsletter.sendMailToSubscriber",
        incoming = listOf("flow_canSendAgain"),
        outgoing = listOf("flow_sendAgainToEventGateway"),
    ),
    FlowNodeDefinition.Gateway(
        id = "eventGateway_afterSendingAgain",
        kind = GatewayKind.EVENT_BASED,
        incoming = listOf("flow_sendAgainToEventGateway"),
        outgoing = listOf("flow_eventGatewayToTimer", "flow_eventGatewayToMailRejectedAgain"),
    ),
    FlowNodeDefinition.Event(
        id = "timer_noRejectionForOneDay",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        incoming = listOf("flow_eventGatewayToTimer"),
        outgoing = listOf("flow_timerToIssueResolved"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1D")),
    ),
    FlowNodeDefinition.Event(
        id = "escalationEndEvent_nofitySupport",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_cannotSendAgain"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Escalation("escalation_notifySupport", "escalation_notifySupport", "200"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "event_mailRejectedAgain",
        shape = EventShape.INTERMEDIATE_CATCH_EVENT,
        incoming = listOf("flow_eventGatewayToMailRejectedAgain"),
        outgoing = listOf("flow_mailRejectedAgainToEscalation"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_MailRejectedAgain", "Message_MailRejectedAgain")),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "escalationEndEvent_nofitySupportAfterRepeatedError",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_mailRejectedAgainToEscalation"),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_issueResolved",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_timerToIssueResolved"),
    ),
)

private fun errorHandlingFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("flow_mailRejectedToAnalyze", "event_mailRejected", "serviceTask_analyzeError"),
    SequenceFlowDefinition("flow_analyzeToCanSendAgain", "serviceTask_analyzeError", "gateway_canSendAgain"),
    SequenceFlowDefinition("flow_canSendAgain", "gateway_canSendAgain", "serviceTask_sendMailAgain", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition("flow_cannotSendAgain", "gateway_canSendAgain", "escalationEndEvent_nofitySupport", flowName = "No", conditionExpression = "\${rejection.reason == \"PERMANENT\"}"),
    SequenceFlowDefinition("flow_sendAgainToEventGateway", "serviceTask_sendMailAgain", "eventGateway_afterSendingAgain"),
    SequenceFlowDefinition("flow_eventGatewayToTimer", "eventGateway_afterSendingAgain", "timer_noRejectionForOneDay"),
    SequenceFlowDefinition("flow_eventGatewayToMailRejectedAgain", "eventGateway_afterSendingAgain", "event_mailRejectedAgain"),
    SequenceFlowDefinition("flow_mailRejectedAgainToEscalation", "event_mailRejectedAgain", "escalationEndEvent_nofitySupportAfterRepeatedError"),
    SequenceFlowDefinition("flow_timerToIssueResolved", "timer_noRejectionForOneDay", "endEvent_issueResolved"),
)

fun sendNewsletterSequenceFlows(): List<SequenceFlowDefinition> = listOf(
    SequenceFlowDefinition("flow_editionToLoadSubscribers", "startEvent_editionCreated", "serviceTask_loadSubscribers"),
    SequenceFlowDefinition("flow_loadSubscribersToGateway", "serviceTask_loadSubscribers", "gateway_hasSubscribers"),
    SequenceFlowDefinition("flow_hasSubscribers", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
    SequenceFlowDefinition("flow_noSubscribers", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}"),
    SequenceFlowDefinition("flow_sendToNotifyAuthor", "serviceTask_sendToSubscriber", "serviceTask_notifyAuthor"),
    SequenceFlowDefinition("flow_notifyAuthorToEditionSent", "serviceTask_notifyAuthor", "endEvent_editionSent"),
)
