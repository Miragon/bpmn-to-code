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

class OperatonExtractionTest {

    private val underTest = ProcessModelReader(CamundaDialect(OPERATON_NAMESPACE))

    @Test
    fun `extract returns valid ProcessModel with operaton namespace`() {
        // given: the Operaton newsletter BPMN file from classpath
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())

        // when: extracting the model
        val bpmnModel = underTest.read(file.readBytes())

        fun node(id: String) = bpmnModel.allFlowNodes.single { it.id == id }

        // --- process-level metadata ---
        assertThat(bpmnModel.processId).isEqualTo("newsletterSubscription")
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.OPERATON)
        assertThat(bpmnModel.isExecutable).isTrue()

        // --- root vs. nested scope ---
        // the five nodes that lived in the sub-process (old parentId "subProcess_confirmation") must not be
        // at the root, but must be reachable through allFlowNodes with their parent set on the graph
        val nestedIds = listOf(
            "userTask_confirmRegistration",
            "serviceTask_sendConfirmationMail",
            "endEvent_subscriptionConfirmed",
            "startEvent_requestReceived",
            "timer_everyDay",
        )
        assertThat(bpmnModel.flowNodes.map { it.id }).containsExactlyInAnyOrder(
            "callActivity_abortRegistration",
            "serviceTask_sendWelcomeMail",
            "serviceTask_notifyCommunity",
            "gateway_splitNotifications",
            "gateway_joinNotifications",
            "compensationEndEvent_registrationAborted",
            "compensationEvent_onSubscriptionCounter",
            "serviceTask_decrementSubscriptionCounter",
            "endEvent_registrationCompleted",
            "endEvent_registrationNotPossible",
            "errorEvent_invalidMail",
            "serviceTask_incrementSubscriptionCounter",
            "startEvent_submitRegistrationForm",
            "subProcess_confirmation",
            "timer_after3Days",
        )
        assertThat(bpmnModel.flowNodes.map { it.id }).doesNotContainAnyElementsOf(nestedIds)
        assertThat(bpmnModel.allFlowNodes.map { it.id }).containsAll(nestedIds)
        nestedIds.forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("subProcess_confirmation") }

        // --- sub-process: kind and children ---
        val subProcess = node("subProcess_confirmation") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.map { it.id }).containsExactlyInAnyOrderElementsOf(nestedIds)

        // --- node kinds ---
        assertThat((node("userTask_confirmRegistration") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.USER)
        val compensationHandler = node("serviceTask_decrementSubscriptionCounter") as FlowNodeDefinition.Activity.Task
        assertThat(compensationHandler.kind).isEqualTo(TaskKind.SERVICE)
        assertThat(compensationHandler.implementation).isEqualTo(TaskImplementation.DelegateExpression("counterClass"))
        assertThat((node("serviceTask_sendWelcomeMail") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.SERVICE)
        assertThat((node("gateway_splitNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat((node("gateway_joinNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat(node("callActivity_abortRegistration")).isInstanceOf(FlowNodeDefinition.Activity.CallActivity::class.java)

        // --- service-task implementations (old IMPL_KIND -> type, IMPL_VALUE -> reference) ---
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["serviceTask_sendWelcomeMail"]).isEqualTo(TaskImplementation.DelegateExpression("newsletter.sendWelcomeMail"))
        assertThat(implementations["serviceTask_sendConfirmationMail"]).isEqualTo(TaskImplementation.ExternalTask("newsletter.sendConfirmationMail"))
        assertThat(implementations["endEvent_registrationCompleted"]).isEqualTo(TaskImplementation.ExternalTask("newsletter.registrationCompleted"))
        assertThat(implementations["serviceTask_incrementSubscriptionCounter"]).isEqualTo(TaskImplementation.DelegateExpression("counterClass"))
        assertThat(implementations["serviceTask_notifyCommunity"]).isEqualTo(TaskImplementation.DelegateExpression("newsletter.notifyCommunity"))

        // --- event definitions ---
        val timerAfter = node("timer_after3Days") as FlowNodeDefinition.Event
        assertThat(timerAfter.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(timerAfter.interrupting).isTrue()
        assertThat(timerAfter.attachedToRef).isEqualTo("subProcess_confirmation")
        assertThat(timerAfter.eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "\${testVariable}"))

        val timerEveryDay = node("timer_everyDay") as FlowNodeDefinition.Event
        assertThat(timerEveryDay.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(timerEveryDay.interrupting).isFalse()
        assertThat(timerEveryDay.attachedToRef).isEqualTo("userTask_confirmRegistration")
        assertThat(timerEveryDay.eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M"))

        val submitForm = node("startEvent_submitRegistrationForm") as FlowNodeDefinition.Event
        assertThat(submitForm.shape).isEqualTo(EventShape.START_EVENT)
        assertThat(submitForm.eventDefinitions.filterIsInstance<EventDefinitionInstance.Message>().single().reference.messageName)
            .isEqualTo("Message_FormSubmitted")

        val notPossible = node("endEvent_registrationNotPossible") as FlowNodeDefinition.Event
        assertThat(notPossible.shape).isEqualTo(EventShape.END_EVENT)
        assertThat(notPossible.eventDefinitions.filterIsInstance<EventDefinitionInstance.Signal>().single().signalName)
            .isEqualTo("Signal_RegistrationNotPossible")

        val invalidMail = node("errorEvent_invalidMail") as FlowNodeDefinition.Event
        assertThat(invalidMail.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(invalidMail.interrupting).isTrue()
        assertThat(invalidMail.attachedToRef).isEqualTo("subProcess_confirmation")
        val error = invalidMail.eventDefinitions.filterIsInstance<EventDefinitionInstance.Error>().single()
        assertThat(error.errorName).isEqualTo("Error_InvalidMail")
        assertThat(error.errorCode).isEqualTo("500")
        assertThat(bpmnModel.definitions.errors.map { it.getValue() }).contains("Error_InvalidMail" to "500")

        val abortedEnd = node("compensationEndEvent_registrationAborted") as FlowNodeDefinition.Event
        assertThat(abortedEnd.shape).isEqualTo(EventShape.END_EVENT)
        assertThat(abortedEnd.eventDefinitions).anyMatch { it is EventDefinitionInstance.Compensation }

        val onCounter = node("compensationEvent_onSubscriptionCounter") as FlowNodeDefinition.Event
        assertThat(onCounter.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(onCounter.interrupting).isTrue()
        assertThat(onCounter.attachedToRef).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(onCounter.eventDefinitions).anyMatch { it is EventDefinitionInstance.Compensation }

        // --- derived timers ---
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("timer_after3Days", TimerType.DURATION, "\${testVariable}"),
            TimerDefinition("timer_everyDay", TimerType.DURATION, "PT1M"),
        )

        // --- derived compensations ---
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition("compensationEndEvent_registrationAborted", CompensationDefinition.Type.THROWING, activityRef = "serviceTask_incrementSubscriptionCounter", waitForCompletion = false),
            CompensationDefinition("compensationEvent_onSubscriptionCounter", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
        )

        // --- call activity ---
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_abortRegistration" }
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
        val subProcessInternalFlows = listOf("flow_requestToConfirmationMail", "flow_everyDayToConfirmationMail", "flow_confirmationMailToConfirm", "flow_confirmToConfirmed")
        assertThat(bpmnModel.sequenceFlows.map { it.id }).doesNotContainAnyElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.sequenceFlows.map { it.id }).contains("flow_confirmationToSplit", "flow_incrementCounterToConfirmation")
        assertThat(subProcess.sequenceFlows.map { it.id }).containsExactlyInAnyOrderElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(15)
        assertThat(bpmnModel.graph.allSequenceFlows).contains(
            SequenceFlowDefinition("flow_confirmationMailToConfirm", "serviceTask_sendConfirmationMail", "userTask_confirmRegistration"),
            SequenceFlowDefinition("flow_after3DaysToAbort", "timer_after3Days", "callActivity_abortRegistration"),
        )

        // --- messages registry ---
        assertThat(bpmnModel.definitions.messages.map { it.getValue() }).contains("Message_FormSubmitted")

        // --- boundary attachments ---
        assertThat(bpmnModel.graph.attachedElementsOf(node("subProcess_confirmation")))
            .containsExactlyInAnyOrder("errorEvent_invalidMail", "timer_after3Days")
        assertThat(bpmnModel.graph.attachedElementsOf(node("serviceTask_incrementSubscriptionCounter")))
            .containsExactly("compensationEvent_onSubscriptionCounter")
        assertThat(bpmnModel.graph.attachedElementsOf(node("userTask_confirmRegistration")))
            .containsExactly("timer_everyDay")

        // --- node-to-node adjacency (derived through sequence flows) ---
        assertThat(bpmnModel.graph.previousElementsOf(node("callActivity_abortRegistration"))).containsExactly("timer_after3Days")
        assertThat(bpmnModel.graph.followingElementsOf(node("callActivity_abortRegistration"))).containsExactly("compensationEndEvent_registrationAborted")
        assertThat(bpmnModel.graph.previousElementsOf(node("subProcess_confirmation"))).containsExactly("serviceTask_incrementSubscriptionCounter")
        assertThat(bpmnModel.graph.followingElementsOf(node("gateway_splitNotifications")))
            .containsExactlyInAnyOrder("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity")
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isNull()
    }

    @Test
    fun `extract returns additionalInputVariables and additionalOutputVariables from operaton properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-additional-variables.bpmn"))
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
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-additional-variables.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        val activity = bpmnModel.allFlowNodes.single { it.id == "Activity_ProcessOrder" }
        assertThat(activity.variables).contains(
            VariableDefinition("orderId", VariableDirection.INPUT, "\${orderId}"),
            VariableDefinition("orderId", VariableDirection.OUTPUT, "\${orderId}"),
        )
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
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
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val flowsById = bpmnModel.sequenceFlows.associateBy { it.id }
        assertThat(flowsById["flow_hasSubscribers"]).isEqualTo(
            SequenceFlowDefinition("flow_hasSubscribers", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
        )
        assertThat(flowsById["flow_noSubscribers"]).isEqualTo(
            SequenceFlowDefinition("flow_noSubscribers", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "\${subscribers.size() > 0}"),
        )
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/operaton-non-executable.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/operaton-subscribe-newsletter.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isTrue()
    }

    private companion object {
        const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"
    }
}
