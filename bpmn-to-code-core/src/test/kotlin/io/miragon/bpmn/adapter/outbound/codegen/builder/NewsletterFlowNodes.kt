package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.EventDefinitionType
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.CallActivityMapping
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition.Companion.IMPL_VALUE_KEY
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

internal fun buildSubscribeNewsletterFlowNodes(
    confirmationMailImpl: String,
    welcomeMailImpl: String,
    registrationCompletedImpl: String,
    extraVariables: List<VariableDefinition> = emptyList(),
) = listOf(
    FlowNodeDefinition(
        id = "CallActivity_AbortRegistration",
        nodeType = BpmnNodeType.Activity.CallActivity,
        properties = FlowNodeProperties.CallActivity(
            CallActivityDefinition(
                id = "CallActivity_AbortRegistration",
                calledElement = "abort-registration",
                mappings = listOf(
                    CallActivityMapping(direction = VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
                    CallActivityMapping(direction = VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
                    CallActivityMapping(direction = VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
                ),
            ),
        ),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)),
        previousElements = listOf("Timer_After3Days"),
        followingElements = listOf("CompensationEndEvent_RegistrationAborted"),
    ),
    FlowNodeDefinition(
        id = "Activity_ConfirmRegistration",
        displayName = "Confirm registration",
        nodeType = BpmnNodeType.Activity.Task(TaskKind.RECEIVE),
        attachedElements = listOf("Timer_EveryDay"),
        parentId = "SubProcess_Confirmation",
        previousElements = listOf("Activity_SendConfirmationMail"),
        followingElements = listOf("EndEvent_SubscriptionConfirmed"),
    ),
    FlowNodeDefinition(
        id = "Activity_SendConfirmationMail",
        nodeType = BpmnNodeType.Activity.Task(TaskKind.SERVICE),
        properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("Activity_SendConfirmationMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to confirmationMailImpl))),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT)) + extraVariables,
        parentId = "SubProcess_Confirmation",
        previousElements = listOf("StartEvent_RequestReceived", "Timer_EveryDay"),
        followingElements = listOf("Activity_ConfirmRegistration"),
    ),
    FlowNodeDefinition(
        id = "Activity_SendWelcomeMail",
        nodeType = BpmnNodeType.Activity.Task(TaskKind.SERVICE),
        properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("Activity_SendWelcomeMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to welcomeMailImpl))),
        variables = listOf(
            VariableDefinition("subscriptionId", VariableDirection.INPUT),
            VariableDefinition("subscriptionId", VariableDirection.OUTPUT),
        ),
        previousElements = listOf("SubProcess_Confirmation"),
        followingElements = listOf("EndEvent_RegistrationCompleted"),
    ),
    FlowNodeDefinition(
        id = "CompensationEndEvent_RegistrationAborted",
        nodeType = BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.COMPENSATION),
        previousElements = listOf("CallActivity_AbortRegistration"),
    ),
    FlowNodeDefinition(
        id = "CompensationEvent_OnSubscriptionCounter",
        nodeType = BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.COMPENSATION),
        attachedToRef = "serviceTask_incrementSubscriptionCounter",
    ),
    FlowNodeDefinition(
        id = "CompensationTask_DecrementSubscriptionCounter",
        nodeType = BpmnNodeType.Activity.Task(TaskKind.NONE),
    ),
    FlowNodeDefinition(
        id = "EndEvent_RegistrationCompleted",
        nodeType = BpmnNodeType.Event(EventShape.END_EVENT),
        properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("EndEvent_RegistrationCompleted", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to registrationCompletedImpl))),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
        previousElements = listOf("Activity_SendWelcomeMail"),
    ),
    FlowNodeDefinition(
        id = "EndEvent_RegistrationNotPossible",
        nodeType = BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.SIGNAL),
        previousElements = listOf("ErrorEvent_InvalidMail"),
    ),
    FlowNodeDefinition(
        id = "EndEvent_SubscriptionConfirmed",
        nodeType = BpmnNodeType.Event(EventShape.END_EVENT),
        parentId = "SubProcess_Confirmation",
        previousElements = listOf("Activity_ConfirmRegistration"),
    ),
    FlowNodeDefinition(
        id = "ErrorEvent_InvalidMail",
        nodeType = BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.ERROR),
        attachedToRef = "SubProcess_Confirmation",
        followingElements = listOf("EndEvent_RegistrationNotPossible"),
    ),
    FlowNodeDefinition(
        id = "serviceTask_incrementSubscriptionCounter",
        nodeType = BpmnNodeType.Activity.Task(TaskKind.SERVICE),
        properties = FlowNodeProperties.ServiceTask(ServiceTaskDefinition("serviceTask_incrementSubscriptionCounter", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "counterClass"))),
        attachedElements = listOf("CompensationEvent_OnSubscriptionCounter"),
        previousElements = listOf("StartEvent_SubmitRegistrationForm"),
        followingElements = listOf("SubProcess_Confirmation"),
    ),
    FlowNodeDefinition(
        id = "StartEvent_RequestReceived",
        nodeType = BpmnNodeType.Event(EventShape.START_EVENT),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
        parentId = "SubProcess_Confirmation",
        followingElements = listOf("Activity_SendConfirmationMail"),
    ),
    FlowNodeDefinition(
        id = "StartEvent_SubmitRegistrationForm",
        nodeType = BpmnNodeType.Event(EventShape.START_EVENT, EventDefinitionType.MESSAGE),
        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT)),
        followingElements = listOf("serviceTask_incrementSubscriptionCounter"),
    ),
    FlowNodeDefinition(
        id = "SubProcess_Confirmation",
        nodeType = BpmnNodeType.Activity.SubProcess(SubProcessKind.PLAIN),
        attachedElements = listOf("ErrorEvent_InvalidMail", "Timer_After3Days"),
        previousElements = listOf("serviceTask_incrementSubscriptionCounter"),
        followingElements = listOf("Activity_SendWelcomeMail"),
    ),
    FlowNodeDefinition(
        id = "Timer_After3Days",
        nodeType = BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
        properties = FlowNodeProperties.Timer(TimerDefinition("Timer_After3Days", "Duration", "\${testVariable}")),
        attachedToRef = "SubProcess_Confirmation",
        followingElements = listOf("CallActivity_AbortRegistration"),
    ),
    FlowNodeDefinition(
        id = "Timer_EveryDay",
        nodeType = BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
        properties = FlowNodeProperties.Timer(TimerDefinition("Timer_EveryDay", "Duration", "PT1M")),
        attachedToRef = "Activity_ConfirmRegistration",
        parentId = "SubProcess_Confirmation",
        followingElements = listOf("Activity_SendConfirmationMail"),
    ),
)
