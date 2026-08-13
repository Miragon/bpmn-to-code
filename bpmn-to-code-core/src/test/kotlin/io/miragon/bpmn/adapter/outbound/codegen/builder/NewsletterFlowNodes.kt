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
        id = "CallActivity_AbortRegistration",
        definition = CallActivityDefinition(
            id = "CallActivity_AbortRegistration",
            calledElement = "abort-registration",
            mappings = listOf(
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
                CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
                CallActivityDefinition.Mapping(direction = VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
            ),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
        incoming = listOf("Flow_1l1lj4m"),
        outgoing = listOf("Flow_1bsb8no"),
    ),
    jobWorkerTask(
        id = "Activity_SendWelcomeMail",
        jobType = welcomeMailImpl,
        incoming = listOf("Flow_16hub0n"),
        outgoing = listOf("Flow_1i7hjid"),
        variables = listOf(
            VariableDefinition("subscriptionId", VariableDirection.INPUT),
            VariableDefinition("subscriptionId", VariableDirection.OUTPUT),
        ),
    ),
    jobWorkerTask(
        id = "Activity_NotifyCommunity",
        jobType = notifyCommunityImpl,
        incoming = listOf("Flow_1p5t47z"),
        outgoing = listOf("Flow_1duwy83"),
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
        incoming = listOf("Flow_1bsb8no"),
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    FlowNodeDefinition.Event(
        id = "CompensationEvent_OnSubscriptionCounter",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "serviceTask_incrementSubscriptionCounter",
        interrupting = true,
        eventDefinitions = listOf(EventDefinitionInstance.Compensation()),
    ),
    jobWorkerTask(
        id = "CompensationTask_DecrementSubscriptionCounter",
        jobType = "counterClass",
    ),
    FlowNodeDefinition.Event(
        id = "EndEvent_RegistrationCompleted",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_1862jd8"),
        implementation = io.miragon.bpmn.domain.shared.TaskImplementation.JobWorker(registrationCompletedImpl),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Event(
        id = "EndEvent_RegistrationNotPossible",
        shape = EventShape.END_EVENT,
        incoming = listOf("Flow_0i2ctuv"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Signal("Signal_RegistrationNotPossible", "Signal_RegistrationNotPossible"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "ErrorEvent_InvalidMail",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "SubProcess_Confirmation",
        interrupting = true,
        outgoing = listOf("Flow_0i2ctuv"),
        eventDefinitions = listOf(EventDefinitionInstance.Error("Error_InvalidMail", "Error_InvalidMail", "500")),
    ),
    jobWorkerTask(
        id = "serviceTask_incrementSubscriptionCounter",
        jobType = "counterClass",
        incoming = listOf("Flow_1csfyyz"),
        outgoing = listOf("Flow_0zdmt0t"),
        boundaryEventRefs = listOf("CompensationEvent_OnSubscriptionCounter"),
    ),
    FlowNodeDefinition.Event(
        id = "StartEvent_SubmitRegistrationForm",
        shape = EventShape.START_EVENT,
        outgoing = listOf("Flow_1csfyyz"),
        eventDefinitions = listOf(
            EventDefinitionInstance.Message(MessageReference("Message_FormSubmitted", "Message_FormSubmitted")),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
    ),
    FlowNodeDefinition.Activity.SubProcess(
        id = "SubProcess_Confirmation",
        kind = SubProcessKind.PLAIN,
        incoming = listOf("Flow_0zdmt0t"),
        outgoing = listOf("Flow_09cuvzp"),
        boundaryEventRefs = listOf("ErrorEvent_InvalidMail", "Timer_After3Days"),
        flowNodes = listOf(
            FlowNodeDefinition.Activity.Task(
                id = "Activity_ConfirmRegistration",
                kind = TaskKind.RECEIVE,
                displayName = "Confirm registration",
                incoming = listOf("Flow_1bckm43"),
                outgoing = listOf("Flow_1cpwe57"),
                boundaryEventRefs = listOf("Timer_EveryDay"),
            ),
            jobWorkerTask(
                id = "Activity_SendConfirmationMail",
                jobType = confirmationMailImpl,
                incoming = listOf("Flow_05i3x1y", "Flow_0x4ewvb"),
                outgoing = listOf("Flow_1bckm43"),
                variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)) + extraVariables,
            ),
            FlowNodeDefinition.Event(
                id = "EndEvent_SubscriptionConfirmed",
                shape = EventShape.END_EVENT,
                incoming = listOf("Flow_1cpwe57"),
            ),
            FlowNodeDefinition.Event(
                id = "StartEvent_RequestReceived",
                shape = EventShape.START_EVENT,
                outgoing = listOf("Flow_05i3x1y"),
                variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
            ),
            FlowNodeDefinition.Event(
                id = "Timer_EveryDay",
                shape = EventShape.BOUNDARY_EVENT,
                attachedToRef = "Activity_ConfirmRegistration",
                interrupting = false,
                outgoing = listOf("Flow_0x4ewvb"),
                eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
            ),
        ),
        sequenceFlows = listOf(
            SequenceFlowDefinition("Flow_05i3x1y", "StartEvent_RequestReceived", "Activity_SendConfirmationMail"),
            SequenceFlowDefinition("Flow_0x4ewvb", "Timer_EveryDay", "Activity_SendConfirmationMail"),
            SequenceFlowDefinition("Flow_1bckm43", "Activity_SendConfirmationMail", "Activity_ConfirmRegistration"),
            SequenceFlowDefinition("Flow_1cpwe57", "Activity_ConfirmRegistration", "EndEvent_SubscriptionConfirmed"),
        ),
    ),
    FlowNodeDefinition.Event(
        id = "Timer_After3Days",
        shape = EventShape.BOUNDARY_EVENT,
        attachedToRef = "SubProcess_Confirmation",
        interrupting = true,
        outgoing = listOf("Flow_1l1lj4m"),
        eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "\${testVariable}")),
    ),
)
