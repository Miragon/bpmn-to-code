package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

@Suppress("LongMethod")
internal fun buildSubscribeNewsletterFlowNodes(
    confirmationMailImpl: String,
    welcomeMailImpl: String,
    registrationCompletedImpl: String,
    notifyCommunityImpl: String,
    extraVariables: List<VariableDefinition> = emptyList(),
) = listOf(
    FlowNodeDefinition.Activity.CallActivity(
        id = "callActivity_abortRegistration",
        definition = CallActivityDefinition(
            id = "callActivity_abortRegistration",
            calledElement = "abort-registration",
            mappings = listOf(
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
                CallActivityDefinition.Mapping(direction = VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
            ),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
        incoming = listOf("flow_after3DaysToAbort"),
        outgoing = listOf("flow_abortToRegistrationAborted"),
    ),
    jobWorkerTask(
        id = "serviceTask_sendWelcomeMail",
        jobType = welcomeMailImpl,
        incoming = listOf("flow_splitToWelcomeMail"),
        outgoing = listOf("flow_welcomeMailToJoin"),
        variables = listOf(
            VariableDefinition("subscriptionId", VariableDirection.INPUT),
            VariableDefinition("subscriptionId", VariableDirection.OUTPUT),
        ),
    ),
    jobWorkerTask(
        id = "serviceTask_notifyCommunity",
        jobType = notifyCommunityImpl,
        incoming = listOf("flow_splitToNotifyCommunity"),
        outgoing = listOf("flow_notifyCommunityToJoin"),
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
        incoming = listOf("flow_abortToRegistrationAborted"),
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Event(
        id = "compensationEvent_onSubscriptionCounter",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "serviceTask_incrementSubscriptionCounter",
        interrupting = true,
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    jobWorkerTask(
        id = "serviceTask_decrementSubscriptionCounter",
        jobType = "counterClass",
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_registrationCompleted",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_joinToRegistrationCompleted"),
        implementation = io.miragon.bpmn.domain.shared.TaskImplementation.JobWorker(registrationCompletedImpl),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "endEvent_registrationNotPossible",
        shape = EventShape.END_EVENT,
        incoming = listOf("flow_invalidMailToNotPossible"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Signal("Signal_RegistrationNotPossible", "Signal_RegistrationNotPossible"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "errorEvent_invalidMail",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "subProcess_confirmation",
        interrupting = true,
        outgoing = listOf("flow_invalidMailToNotPossible"),
        eventDefinitions = listOf(EventDefinitionInstance.Error("Error_InvalidMail", "Error_InvalidMail", "500")),
    ),
    jobWorkerTask(
        id = "serviceTask_incrementSubscriptionCounter",
        jobType = "counterClass",
        incoming = listOf("flow_submitToIncrementCounter"),
        outgoing = listOf("flow_incrementCounterToConfirmation"),
        boundaryEventRefs = listOf("compensationEvent_onSubscriptionCounter"),
    ),
    FlowNodeDefinition.Event(
        id = "startEvent_submitRegistrationForm",
        shape = EventShape.START_EVENT,
        outgoing = listOf("flow_submitToIncrementCounter"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_FormSubmitted", "Message_FormSubmitted")),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "subProcess_confirmation",
        kind = SubProcessKind.PLAIN,
        incoming = listOf("flow_incrementCounterToConfirmation"),
        outgoing = listOf("flow_confirmationToSplit"),
        boundaryEventRefs = listOf("errorEvent_invalidMail", "timer_after3Days"),
        flowNodes = listOf(
            FlowNodeDefinition.Activity.Task(
                id = "receiveTask_confirmRegistration",
                kind = TaskKind.RECEIVE,
                displayName = "Confirm registration",
                incoming = listOf("flow_confirmationMailToConfirm"),
                outgoing = listOf("flow_confirmToConfirmed"),
                boundaryEventRefs = listOf("timer_everyDay"),
            ),
            jobWorkerTask(
                id = "serviceTask_sendConfirmationMail",
                jobType = confirmationMailImpl,
                incoming = listOf("flow_requestToConfirmationMail", "flow_everyDayToConfirmationMail"),
                outgoing = listOf("flow_confirmationMailToConfirm"),
                variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)) + extraVariables,
            ),
            FlowNodeDefinition.Event(
                id = "endEvent_subscriptionConfirmed",
                shape = EventShape.END_EVENT,
                incoming = listOf("flow_confirmToConfirmed"),
            ),
            FlowNodeDefinition.Event(
                id = "startEvent_requestReceived",
                shape = EventShape.START_EVENT,
                outgoing = listOf("flow_requestToConfirmationMail"),
                variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
            ),
            FlowNodeDefinition.Event(
                id = "timer_everyDay",
                shape = EventShape.BOUNDARY_EVENT,
                attachedToRef = "receiveTask_confirmRegistration",
                interrupting = false,
                outgoing = listOf("flow_everyDayToConfirmationMail"),
                eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
            ),
        ),
        sequenceFlows = listOf(
            SequenceFlowDefinition("flow_requestToConfirmationMail", "startEvent_requestReceived", "serviceTask_sendConfirmationMail"),
            SequenceFlowDefinition("flow_everyDayToConfirmationMail", "timer_everyDay", "serviceTask_sendConfirmationMail"),
            SequenceFlowDefinition("flow_confirmationMailToConfirm", "serviceTask_sendConfirmationMail", "receiveTask_confirmRegistration"),
            SequenceFlowDefinition("flow_confirmToConfirmed", "receiveTask_confirmRegistration", "endEvent_subscriptionConfirmed"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "timer_after3Days",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "subProcess_confirmation",
        interrupting = true,
        outgoing = listOf("flow_after3DaysToAbort"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "\${testVariable}")),
    ),
)
