package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.CompensationDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class Camunda7ExtractionTest {

    private val underTest = ProcessModelReader(CamundaDialect(CAMUNDA_7_NAMESPACE))

    @Test
    fun `extract returns valid ProcessModel`() {
        // given: the Camunda 7 newsletter BPMN file from classpath
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())

        // when: extracting the model
        val bpmnModel = underTest.read(file.readBytes())

        fun node(id: String) = bpmnModel.allFlowNodes.single { it.id == id }

        // --- process-level metadata ---
        assertThat(bpmnModel.processId).isEqualTo("newsletterSubscription")
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.CAMUNDA_7)
        assertThat(bpmnModel.isExecutable).isTrue()

        // --- root vs. nested scope ---
        // the five nodes that lived in the sub-process (old parentId "SubProcess_Confirmation") must not be
        // at the root, but must be reachable through allFlowNodes with their parent set on the graph
        val nestedIds = listOf(
            "Activity_ConfirmRegistration",
            "Activity_SendConfirmationMail",
            "EndEvent_SubscriptionConfirmed",
            "StartEvent_RequestReceived",
            "Timer_EveryDay",
        )
        assertThat(bpmnModel.flowNodes.map { it.id }).containsExactlyInAnyOrder(
            "CallActivity_AbortRegistration",
            "Activity_SendWelcomeMail",
            "Activity_NotifyCommunity",
            "Gateway_SplitNotifications",
            "Gateway_JoinNotifications",
            "CompensationEndEvent_RegistrationAborted",
            "CompensationEvent_OnSubscriptionCounter",
            "CompensationTask_DecrementSubscriptionCounter",
            "EndEvent_RegistrationCompleted",
            "EndEvent_RegistrationNotPossible",
            "ErrorEvent_InvalidMail",
            "serviceTask_incrementSubscriptionCounter",
            "StartEvent_SubmitRegistrationForm",
            "SubProcess_Confirmation",
            "Timer_After3Days",
        )
        assertThat(bpmnModel.flowNodes.map { it.id }).doesNotContainAnyElementsOf(nestedIds)
        assertThat(bpmnModel.allFlowNodes.map { it.id }).containsAll(nestedIds)
        nestedIds.forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("SubProcess_Confirmation") }

        // --- sub-process: kind and children ---
        val subProcess = node("SubProcess_Confirmation") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.map { it.id }).containsExactlyInAnyOrderElementsOf(nestedIds)

        // --- node kinds ---
        assertThat((node("Activity_ConfirmRegistration") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.USER)
        val compensationHandler = node("CompensationTask_DecrementSubscriptionCounter") as FlowNodeDefinition.Activity.Task
        assertThat(compensationHandler.kind).isEqualTo(TaskKind.SERVICE)
        assertThat(compensationHandler.implementation).isEqualTo(TaskImplementation.DelegateExpression("counterClass"))
        assertThat((node("Activity_SendWelcomeMail") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.SERVICE)
        assertThat((node("Gateway_SplitNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat((node("Gateway_JoinNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat(node("CallActivity_AbortRegistration")).isInstanceOf(FlowNodeDefinition.Activity.CallActivity::class.java)

        // --- service-task implementations (old IMPL_KIND -> type, IMPL_VALUE -> reference) ---
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["Activity_SendWelcomeMail"]).isEqualTo(TaskImplementation.DelegateExpression("\${newsletterSendWelcomeMail}"))
        assertThat(implementations["Activity_SendConfirmationMail"]).isEqualTo(TaskImplementation.ExternalTask("#{newsletterSendConfirmationMail}"))
        assertThat(implementations["EndEvent_RegistrationCompleted"]).isEqualTo(TaskImplementation.ExternalTask("newsletter.registrationCompleted"))
        assertThat(implementations["serviceTask_incrementSubscriptionCounter"]).isEqualTo(TaskImplementation.DelegateExpression("counterClass"))
        assertThat(implementations["Activity_NotifyCommunity"]).isEqualTo(TaskImplementation.DelegateExpression("\${newsletterNotifyCommunity}"))

        // --- event definitions ---
        val timerAfter = node("Timer_After3Days") as FlowNodeDefinition.Event
        assertThat(timerAfter.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(timerAfter.interrupting).isTrue()
        assertThat(timerAfter.attachedToRef).isEqualTo("SubProcess_Confirmation")
        assertThat(timerAfter.eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "\${testVariable}"))

        val timerEveryDay = node("Timer_EveryDay") as FlowNodeDefinition.Event
        assertThat(timerEveryDay.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(timerEveryDay.interrupting).isFalse()
        assertThat(timerEveryDay.attachedToRef).isEqualTo("Activity_ConfirmRegistration")
        assertThat(timerEveryDay.eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M"))

        val submitForm = node("StartEvent_SubmitRegistrationForm") as FlowNodeDefinition.Event
        assertThat(submitForm.shape).isEqualTo(EventShape.START_EVENT)
        assertThat(submitForm.eventDefinitions.filterIsInstance<EventDefinitionInstance.Message>().single().reference.messageName)
            .isEqualTo("Message_FormSubmitted")

        val notPossible = node("EndEvent_RegistrationNotPossible") as FlowNodeDefinition.Event
        assertThat(notPossible.shape).isEqualTo(EventShape.END_EVENT)
        assertThat(notPossible.eventDefinitions.filterIsInstance<EventDefinitionInstance.Signal>().single().signalName)
            .isEqualTo("Signal_RegistrationNotPossible")

        val invalidMail = node("ErrorEvent_InvalidMail") as FlowNodeDefinition.Event
        assertThat(invalidMail.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(invalidMail.interrupting).isTrue()
        assertThat(invalidMail.attachedToRef).isEqualTo("SubProcess_Confirmation")
        val error = invalidMail.eventDefinitions.filterIsInstance<EventDefinitionInstance.Error>().single()
        assertThat(error.errorName).isEqualTo("Error_InvalidMail")
        assertThat(error.errorCode).isEqualTo("500")
        assertThat(bpmnModel.definitions.errors.map { it.getValue() }).contains("Error_InvalidMail" to "500")

        val abortedEnd = node("CompensationEndEvent_RegistrationAborted") as FlowNodeDefinition.Event
        assertThat(abortedEnd.shape).isEqualTo(EventShape.END_EVENT)
        assertThat(abortedEnd.eventDefinitions).anyMatch { it is EventDefinitionInstance.Compensation }

        val onCounter = node("CompensationEvent_OnSubscriptionCounter") as FlowNodeDefinition.Event
        assertThat(onCounter.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(onCounter.interrupting).isTrue()
        assertThat(onCounter.attachedToRef).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(onCounter.eventDefinitions).anyMatch { it is EventDefinitionInstance.Compensation }

        // --- derived timers ---
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("Timer_After3Days", TimerType.DURATION, "\${testVariable}"),
            TimerDefinition("Timer_EveryDay", TimerType.DURATION, "PT1M"),
        )

        // --- derived compensations ---
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition("CompensationEndEvent_RegistrationAborted", CompensationDefinition.Type.THROWING, activityRef = "serviceTask_incrementSubscriptionCounter", waitForCompletion = false),
            CompensationDefinition("CompensationEvent_OnSubscriptionCounter", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
        )

        // --- call activity ---
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
        assertThat(callActivity.hasCalledElement()).isTrue()
        assertThat(callActivity.getValue()).isEqualTo("abort-registration")
        assertThat(callActivity.inputMappings).containsExactlyInAnyOrder(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
            CallActivityDefinition.Mapping(VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
        )
        assertThat(callActivity.outputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
        )
        assertThat(callActivity.propagateAllInputVariables).isNull()
        assertThat(callActivity.propagateAllOutputVariables).isNull()

        // --- sequence flows: root scope vs. sub-process scope ---
        val subProcessInternalFlows = listOf("Flow_05i3x1y", "Flow_0x4ewvb", "Flow_1bckm43", "Flow_1cpwe57")
        assertThat(bpmnModel.sequenceFlows.map { it.id }).doesNotContainAnyElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.sequenceFlows.map { it.id }).contains("Flow_09cuvzp", "Flow_0zdmt0t")
        assertThat(subProcess.sequenceFlows.map { it.id }).containsExactlyInAnyOrderElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(15)
        assertThat(bpmnModel.graph.allSequenceFlows).contains(
            SequenceFlowDefinition("Flow_1bckm43", "Activity_SendConfirmationMail", "Activity_ConfirmRegistration"),
            SequenceFlowDefinition("Flow_1l1lj4m", "Timer_After3Days", "CallActivity_AbortRegistration"),
        )

        // --- messages registry ---
        assertThat(bpmnModel.definitions.messages.map { it.getValue() }).contains("Message_FormSubmitted")

        // --- boundary attachments ---
        assertThat(bpmnModel.graph.attachedElementsOf(node("SubProcess_Confirmation")))
            .containsExactlyInAnyOrder("ErrorEvent_InvalidMail", "Timer_After3Days")
        assertThat(bpmnModel.graph.attachedElementsOf(node("serviceTask_incrementSubscriptionCounter")))
            .containsExactly("CompensationEvent_OnSubscriptionCounter")
        assertThat(bpmnModel.graph.attachedElementsOf(node("Activity_ConfirmRegistration")))
            .containsExactly("Timer_EveryDay")

        // --- node-to-node adjacency (derived through sequence flows) ---
        assertThat(bpmnModel.graph.previousElementsOf(node("CallActivity_AbortRegistration"))).containsExactly("Timer_After3Days")
        assertThat(bpmnModel.graph.followingElementsOf(node("CallActivity_AbortRegistration"))).containsExactly("CompensationEndEvent_RegistrationAborted")
        assertThat(bpmnModel.graph.previousElementsOf(node("SubProcess_Confirmation"))).containsExactly("serviceTask_incrementSubscriptionCounter")
        assertThat(bpmnModel.graph.followingElementsOf(node("Gateway_SplitNotifications")))
            .containsExactlyInAnyOrder("Activity_SendWelcomeMail", "Activity_NotifyCommunity")
    }

    @Test
    fun `extract captures call-activity input and output mapping targets`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn"))
        val bpmnModel = underTest.read(File(resourceUrl.toURI()).readBytes())
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
        assertThat(callActivity.inputMappings).containsExactlyInAnyOrder(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "subscriptionId", target = "childSubscriptionId"),
            CallActivityDefinition.Mapping(VariableDirection.INPUT, sourceExpression = "\${reasonCode}", target = "childReasonCode"),
        )
        assertThat(callActivity.outputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = "childAbortResult", target = "abortResult"),
        )
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isNull()
    }

    @Test
    fun `extract returns additionalInputVariables and additionalOutputVariables from camunda properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
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
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        val activity = bpmnModel.flowNodes.single { it.id == "Activity_ProcessOrder" }
        assertThat(activity.variables).contains(
            VariableDefinition("orderId", VariableDirection.INPUT, "\${orderId}"),
            VariableDefinition("orderId", VariableDirection.OUTPUT, "\${orderId}"),
        )
    }

    @Test
    fun `extract returns additionalInputVariables for non-interrupting message start event in event subprocess`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        // StartEvent_OrderCancelled is nested inside an event sub-process, so it lives under allFlowNodes
        val startEvent = bpmnModel.allFlowNodes.single { it.id == "StartEvent_OrderCancelled" }
        assertThat(startEvent.variables).containsExactlyInAnyOrder(
            VariableDefinition("cancellationReason", VariableDirection.INPUT),
            VariableDefinition("retryCount", VariableDirection.INPUT),
        )
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variables).containsExactlyInAnyOrder(
            VariableDefinition("test", VariableDirection.INPUT, "null"),
            VariableDefinition("authors", VariableDirection.INPUT, "\${authors}"),
            VariableDefinition("author", VariableDirection.INPUT, "author"),
            VariableDefinition("author", VariableDirection.OUTPUT, "\${author}"),
            VariableDefinition("subscribers", VariableDirection.INPUT, "\${subscribers}"),
            VariableDefinition("subscribers", VariableDirection.OUTPUT, "\${subscribers}"),
            VariableDefinition("subscriber", VariableDirection.INPUT, "subscriber"),
        )
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val eventSubProcess = bpmnModel.flowNodes.single { it.id == "eventSubProcess_errorHandling" }
        assertThat(eventSubProcess).isInstanceOf(FlowNodeDefinition.Activity.SubProcess::class.java)
        assertThat((eventSubProcess as FlowNodeDefinition.Activity.SubProcess).kind).isEqualTo(SubProcessKind.EVENT)

        // the event subprocess start event carries the isInterrupting flag; a regular start event has none.
        // event_mailRejected is nested in the event sub-process, so it lives under allFlowNodes.
        val mailRejected = bpmnModel.allFlowNodes.single { it.id == "event_mailRejected" } as FlowNodeDefinition.Event
        assertThat(mailRejected.interrupting).isTrue()
        val editionCreated = bpmnModel.allFlowNodes.single { it.id == "startEvent_editionCreated" } as FlowNodeDefinition.Event
        assertThat(editionCreated.interrupting).isNull()

        // both escalation end events reference the same bpmn:Escalation root element, so the registry — now
        // keyed by that root element — holds a single entry (name-to-code via getValue()).
        assertThat(bpmnModel.definitions.escalations.map { it.getValue() }).containsExactly("escalation_notifySupport" to "200")
        listOf("escalationEndEvent_nofitySupport", "escalationEndEvent_nofitySupportAfterRepeatedError").forEach { id ->
            val event = bpmnModel.allFlowNodes.single { it.id == id } as FlowNodeDefinition.Event
            val escalation = event.eventDefinitions.filterIsInstance<EventDefinitionInstance.Escalation>().single()
            assertThat(escalation.escalationName).isEqualTo("escalation_notifySupport")
            assertThat(escalation.escalationCode).isEqualTo("200")
        }
    }

    @Test
    fun `extract marks default sequence flow correctly`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val flowsById = bpmnModel.sequenceFlows.associateBy { it.id }
        assertThat(flowsById["Flow_1jogut0"]).isEqualTo(
            SequenceFlowDefinition("Flow_1jogut0", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
        )
        assertThat(flowsById["Flow_1gsz7wd"]).isEqualTo(
            SequenceFlowDefinition("Flow_1gsz7wd", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}"),
        )
    }

    @Test
    fun `extract captures propagate-all and keeps named mappings alongside variables=all`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="propagate-all-process" isExecutable="true">
                <bpmn:callActivity id="CallActivity_PropagateAll" name="Propagate all" calledElement="child-process">
                  <bpmn:extensionElements>
                    <camunda:in source="orderId" target="businessKey" />
                    <camunda:in variables="all" />
                    <camunda:out variables="all" />
                  </bpmn:extensionElements>
                </bpmn:callActivity>
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()

        val bpmnModel = underTest.read(xml.toByteArray())

        val callActivity = bpmnModel.callActivities.single()
        assertThat(callActivity.propagateAllInputVariables).isTrue()
        assertThat(callActivity.propagateAllOutputVariables).isTrue()
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "orderId", target = "businessKey"),
        )
    }

    @Test
    fun `extract leaves propagate-all null when variables=all is not declared`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
        assertThat(callActivity.propagateAllInputVariables).isNull()
        assertThat(callActivity.propagateAllOutputVariables).isNull()
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c7-non-executable.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isTrue()
    }

    @Test
    fun `extract treats an absent isExecutable attribute as executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c7-no-executable-attr.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isTrue()
    }

    @Test
    fun `extract keeps root elements that no flow node references`() {
        // given: the fixture declares Message_SubscriptionConfirmed but no element points at it
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn")).toURI())

        // when
        val bpmnModel = underTest.read(file.readBytes())

        // then: the model mirrors the file rather than silently dropping the declaration —
        // UnreferencedRootElementRule is what reports it
        assertThat(bpmnModel.definitions.messages.map { it.getValue() })
            .containsExactlyInAnyOrder("Message_FormSubmitted", "Message_SubscriptionConfirmed")
        assertThat(bpmnModel.referencedDefinitionIds()).doesNotContain("Message_36dkcng")
    }

    private companion object {
        const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
    }
}
