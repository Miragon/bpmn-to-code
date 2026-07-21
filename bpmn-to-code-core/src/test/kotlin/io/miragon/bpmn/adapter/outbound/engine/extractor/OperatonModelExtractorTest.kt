package io.miragon.bpmn.adapter.outbound.engine.extractor

import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.EventDefinitionType
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.CallActivityMapping
import io.miragon.bpmn.domain.shared.CompensationDefinition
import io.miragon.bpmn.domain.shared.CompensationType
import io.miragon.bpmn.domain.shared.EscalationDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.ASYNC_AFTER_KEY
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.ASYNC_BEFORE_KEY
import io.miragon.bpmn.domain.shared.FlowNodeDefinition.Companion.EXCLUSIVE_KEY
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDirection
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition.Companion.IMPL_KIND_KEY
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition.Companion.IMPL_VALUE_KEY
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testSubscribeNewsletterBpmnModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class OperatonModelExtractorTest {

    private val underTest = OperatonModelExtractor()

    @Test
    fun `extract returns valid BpmnModel with operaton namespace`() {

        // given: the Operaton newsletter BPMN file from classpath
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())

        // when: extracting the model
        val bpmnModel = underTest.extract(file.readBytes())

        // then: the model matches the expected structure
        val opServiceTasks = listOf(
            ServiceTaskDefinition("Activity_SendWelcomeMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendWelcomeMail", IMPL_KIND_KEY to "DELEGATE_EXPRESSION")),
            ServiceTaskDefinition("Activity_SendConfirmationMail", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.sendConfirmationMail", IMPL_KIND_KEY to "EXTERNAL_TASK")),
            ServiceTaskDefinition("EndEvent_RegistrationCompleted", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "newsletter.registrationCompleted", IMPL_KIND_KEY to "EXTERNAL_TASK")),
            ServiceTaskDefinition("serviceTask_incrementSubscriptionCounter", engineSpecificProperties = mapOf(IMPL_VALUE_KEY to "counterClass", IMPL_KIND_KEY to "DELEGATE_EXPRESSION")),
        )
        val opServiceTaskById = opServiceTasks.associateBy { it.id }

        assertThat(bpmnModel).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(
            testSubscribeNewsletterBpmnModel(
                variantName = "withApproval",
                detectedEngine = ProcessEngine.OPERATON,
                flowNodes = listOf(
                    FlowNodeDefinition("CallActivity_AbortRegistration", BpmnNodeType.Activity.CallActivity,
                        displayName = "Abort registration",
                        properties = FlowNodeProperties.CallActivity(CallActivityDefinition("CallActivity_AbortRegistration", "abort-registration",
                            mappings = listOf(
                                CallActivityMapping(VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
                                CallActivityMapping(VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
                                CallActivityMapping(VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
                            ))),
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT, "subscriptionId"), VariableDefinition("reasonCode", VariableDirection.INPUT, "\${reasonCode}"), VariableDefinition("abortResult", VariableDirection.OUTPUT, "abortResult")),
                        previousElements = listOf("Timer_After3Days"),
                        followingElements = listOf("CompensationEndEvent_RegistrationAborted"),
                        engineSpecificProperties = mapOf(ASYNC_BEFORE_KEY to true, ASYNC_AFTER_KEY to true)),
                    FlowNodeDefinition("Activity_ConfirmRegistration", BpmnNodeType.Activity.Task(TaskKind.USER),
                        displayName = "Confirm subscription",
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT, "\${subscriptionId}")),
                        attachedElements = listOf("Timer_EveryDay"),
                        parentId = "SubProcess_Confirmation",
                        previousElements = listOf("Activity_SendConfirmationMail"),
                        followingElements = listOf("EndEvent_SubscriptionConfirmed"),
                        engineSpecificProperties = mapOf(ASYNC_AFTER_KEY to true)),
                    FlowNodeDefinition("Activity_SendConfirmationMail", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
                        displayName = "Send confirmation mail",
                        properties = FlowNodeProperties.ServiceTask(opServiceTaskById["Activity_SendConfirmationMail"]!!),
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT, "\${subscriptionId}"), VariableDefinition("otherVariable", VariableDirection.INPUT, "dummy")),
                        parentId = "SubProcess_Confirmation",
                        previousElements = listOf("StartEvent_RequestReceived", "Timer_EveryDay"),
                        followingElements = listOf("Activity_ConfirmRegistration")),
                    FlowNodeDefinition("Activity_SendWelcomeMail", BpmnNodeType.Activity.Task(TaskKind.SERVICE),
                        displayName = "Send Welcome-Mail",
                        properties = FlowNodeProperties.ServiceTask(opServiceTaskById["Activity_SendWelcomeMail"]!!),
                        variables = listOf(
                            VariableDefinition("subscriptionId", VariableDirection.INPUT, "\${subscriptionId}"),
                            VariableDefinition("subscriptionId", VariableDirection.OUTPUT, "\${subscriptionId}"),
                        ),
                        previousElements = listOf("SubProcess_Confirmation"),
                        followingElements = listOf("EndEvent_RegistrationCompleted"),
                        engineSpecificProperties = mapOf(ASYNC_BEFORE_KEY to true, ASYNC_AFTER_KEY to true, EXCLUSIVE_KEY to false)),
                    FlowNodeDefinition("CompensationEndEvent_RegistrationAborted", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.COMPENSATION),
                        displayName = "Registration aborted",
                        previousElements = listOf("CallActivity_AbortRegistration")),
                    FlowNodeDefinition("CompensationEvent_OnSubscriptionCounter", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.COMPENSATION),
                        displayName = "Registration aborted",
                        attachedToRef = "serviceTask_incrementSubscriptionCounter"),
                    FlowNodeDefinition("CompensationTask_DecrementSubscriptionCounter", BpmnNodeType.Activity.Task(TaskKind.NONE),
                        displayName = "Decrement subscription counter",
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT, "\${subscriptionId}"))),
                    FlowNodeDefinition("EndEvent_RegistrationCompleted", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.MESSAGE),
                        displayName = "Registration completed",
                        properties = FlowNodeProperties.ServiceTask(opServiceTaskById["EndEvent_RegistrationCompleted"]!!),
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.INPUT, "\${subscriptionId}")),
                        previousElements = listOf("Activity_SendWelcomeMail")),
                    FlowNodeDefinition("EndEvent_RegistrationNotPossible", BpmnNodeType.Event(EventShape.END_EVENT, EventDefinitionType.SIGNAL),
                        displayName = "Registration not possible",
                        previousElements = listOf("ErrorEvent_InvalidMail"),
                        engineSpecificProperties = mapOf(ASYNC_BEFORE_KEY to true, EXCLUSIVE_KEY to false)),
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
                        properties = FlowNodeProperties.ServiceTask(opServiceTaskById["serviceTask_incrementSubscriptionCounter"]!!),
                        attachedElements = listOf("CompensationEvent_OnSubscriptionCounter"),
                        previousElements = listOf("StartEvent_SubmitRegistrationForm"),
                        followingElements = listOf("SubProcess_Confirmation")),
                    FlowNodeDefinition("StartEvent_RequestReceived", BpmnNodeType.Event(EventShape.START_EVENT),
                        displayName = "Subscription requested",
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT, "\${subscriptionId}")),
                        parentId = "SubProcess_Confirmation",
                        followingElements = listOf("Activity_SendConfirmationMail"),
                        engineSpecificProperties = mapOf(ASYNC_BEFORE_KEY to true)),
                    FlowNodeDefinition("StartEvent_SubmitRegistrationForm", BpmnNodeType.Event(EventShape.START_EVENT, EventDefinitionType.MESSAGE),
                        displayName = "Submit newsletter form",
                        properties = FlowNodeProperties.MessageEvent("Message_FormSubmitted", MessageDirection.CATCH),
                        variables = listOf(VariableDefinition("subscriptionId", VariableDirection.OUTPUT, "\${subscriptionId}")),
                        followingElements = listOf("serviceTask_incrementSubscriptionCounter")),
                    FlowNodeDefinition("SubProcess_Confirmation", BpmnNodeType.Activity.SubProcess(SubProcessKind.PLAIN),
                        displayName = "Subscription Confirmation",
                        attachedElements = listOf("ErrorEvent_InvalidMail", "Timer_After3Days"),
                        previousElements = listOf("serviceTask_incrementSubscriptionCounter"),
                        followingElements = listOf("Activity_SendWelcomeMail")),
                    FlowNodeDefinition("Timer_After3Days", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
                        displayName = "After 3 days",
                        properties = FlowNodeProperties.Timer(TimerDefinition("Timer_After3Days", "Duration", "\${testVariable}")),
                        attachedToRef = "SubProcess_Confirmation",
                        followingElements = listOf("CallActivity_AbortRegistration")),
                    FlowNodeDefinition("Timer_EveryDay", BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, EventDefinitionType.TIMER),
                        displayName = "Every day",
                        properties = FlowNodeProperties.Timer(TimerDefinition("Timer_EveryDay", "Duration", "PT1M")),
                        attachedToRef = "Activity_ConfirmRegistration",
                        parentId = "SubProcess_Confirmation",
                        followingElements = listOf("Activity_SendConfirmationMail")),
                ),
                sequenceFlows = listOf(
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
                compensations = listOf(
                    CompensationDefinition("CompensationEndEvent_RegistrationAborted", CompensationType.THROWING, engineSpecificProperties = mapOf("activityRef" to "serviceTask_incrementSubscriptionCounter", "waitForCompletion" to false)),
                    CompensationDefinition("CompensationEvent_OnSubscriptionCounter", CompensationType.CATCHING, engineSpecificProperties = mapOf("waitForCompletion" to false)),
                ),
            )
        )
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.variantName).isNull()
    }

    @Test
    fun `extract returns additionalInputVariables and additionalOutputVariables from operaton properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.variables).containsExactlyInAnyOrder(
            VariableDefinition("orderId", VariableDirection.INPUT, "\${orderId}"),
            VariableDefinition("orderId", VariableDirection.OUTPUT, "\${orderId}"),
            VariableDefinition("orderId", VariableDirection.INPUT),
            VariableDefinition("orderId", VariableDirection.OUTPUT),
            VariableDefinition("customerEmail", VariableDirection.OUTPUT),
            VariableDefinition("amount", VariableDirection.OUTPUT),
            VariableDefinition("shipmentId", VariableDirection.OUTPUT),
            VariableDefinition("cancellationReason", VariableDirection.INPUT),
            VariableDefinition("retryCount", VariableDirection.INPUT),
        )
    }

    @Test
    fun `extract preserves direction when the same variable name is both input and output on one element`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        val activity = bpmnModel.flowNodes.single { it.id == "Activity_ProcessOrder" }
        assertThat(activity.variables).contains(
            VariableDefinition("orderId", VariableDirection.INPUT, "\${orderId}"),
            VariableDefinition("orderId", VariableDirection.OUTPUT, "\${orderId}"),
        )
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.variables).containsExactlyInAnyOrder(
            VariableDefinition("authors", VariableDirection.INPUT, "authors"),
            VariableDefinition("author", VariableDirection.INPUT, "author"),
            VariableDefinition("author", VariableDirection.OUTPUT, "\${author}"),
            VariableDefinition("subscribers", VariableDirection.INPUT, "subscribers"),
            VariableDefinition("subscribers", VariableDirection.OUTPUT, "\${subscribers}"),
            VariableDefinition("subscriber", VariableDirection.INPUT, "subscriber"),
        )
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())

        val eventSubProcess = bpmnModel.flowNodes.first { it.id == "eventSubProcess_errorHandling" }
        assertThat(eventSubProcess.nodeType).isEqualTo(BpmnNodeType.Activity.SubProcess(SubProcessKind.EVENT))

        assertThat(bpmnModel.escalations).containsExactlyInAnyOrder(
            EscalationDefinition("escalationEndEvent_nofitySupport", "escalation_notifySupport", "200"),
            EscalationDefinition("escalationEndEvent_nofitySupportAfterRepeatedError", "escalation_notifySupport", "200"),
        )
    }

    @Test
    fun `extract marks default sequence flow correctly`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.extract(file.readBytes())

        val flowsById = bpmnModel.sequenceFlows.associateBy { it.id }
        assertThat(flowsById["Flow_1jogut0"]).isEqualTo(
            SequenceFlowDefinition("Flow_1jogut0", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true)
        )
        assertThat(flowsById["Flow_1gsz7wd"]).isEqualTo(
            SequenceFlowDefinition("Flow_1gsz7wd", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}")
        )
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/operaton-non-executable.bpmn")).toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn")).toURI())
        val bpmnModel = underTest.extract(file.readBytes())
        assertThat(bpmnModel.isExecutable).isTrue()
    }

}
