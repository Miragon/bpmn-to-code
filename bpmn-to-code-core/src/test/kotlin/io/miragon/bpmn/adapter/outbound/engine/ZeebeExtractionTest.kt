package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
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

class ZeebeExtractionTest {

    private val underTest = ProcessModelReader(ZeebeDialect())

    @Test
    fun `extract returns a fully populated ProcessModel`() {
        // given: the Camunda 8 / Zeebe newsletter BPMN file from classpath
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn"))
        val bpmnModel = underTest.read(File(resourceUrl.toURI()).readBytes())

        fun node(id: String): FlowNodeDefinition = bpmnModel.allFlowNodes.single { it.id == id }
        fun event(id: String): FlowNodeDefinition.Event = node(id) as FlowNodeDefinition.Event

        // process-level metadata
        assertThat(bpmnModel.processId).isEqualTo("newsletterSubscription")
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.ZEEBE)
        assertThat(bpmnModel.isExecutable).isTrue()

        // the root scope holds only root-level nodes; the confirmation sub-process owns its own children
        assertThat(bpmnModel.flowNodes.mapNotNull { it.id }).containsExactlyInAnyOrder(
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

        // the sub-process nests its children and reports them through the flat view with the right parent
        val subProcess = node("subProcess_confirmation") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.mapNotNull { it.id }).containsExactlyInAnyOrder(
            "userTask_confirmRegistration",
            "serviceTask_sendConfirmationMail",
            "endEvent_subscriptionConfirmed",
            "startEvent_requestReceived",
            "timer_everyDay",
        )
        listOf(
            "userTask_confirmRegistration",
            "serviceTask_sendConfirmationMail",
            "endEvent_subscriptionConfirmed",
            "startEvent_requestReceived",
            "timer_everyDay",
        ).forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("subProcess_confirmation") }
        assertThat(bpmnModel.graph.parentIdOf("callActivity_abortRegistration")).isNull()

        // node kinds
        assertThat((node("gateway_splitNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat((node("gateway_joinNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        val compensationHandler = node("serviceTask_decrementSubscriptionCounter") as FlowNodeDefinition.Activity.Task
        assertThat(compensationHandler.kind).isEqualTo(TaskKind.SERVICE)
        // a serviceTask, but the Zeebe fixture configures no zeebe:taskDefinition for it
        assertThat(compensationHandler.implementation).isNull()

        // the receive task references its message directly (not through an event definition)
        val confirmRegistration = node("userTask_confirmRegistration") as FlowNodeDefinition.Activity.Task
        assertThat(confirmRegistration.kind).isEqualTo(TaskKind.RECEIVE)
        assertThat(confirmRegistration.message?.messageName).isEqualTo("Message_SubscriptionConfirmed")

        // service-task-like implementations are all Zeebe job workers
        val implementationsById = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementationsById["serviceTask_sendConfirmationMail"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.sendConfirmationMail"))
        assertThat(implementationsById["serviceTask_sendWelcomeMail"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.sendWelcomeMail"))
        assertThat(implementationsById["serviceTask_notifyCommunity"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.notifyCommunity"))
        assertThat(implementationsById["serviceTask_incrementSubscriptionCounter"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.incrementCounter"))
        assertThat(implementationsById["endEvent_registrationCompleted"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.registrationCompleted"))

        // event definitions
        assertThat(event("timer_after3Days").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "=testVariable"))
        assertThat(event("timer_everyDay").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M"))
        val formMessage = event("startEvent_submitRegistrationForm").eventDefinitions
            .filterIsInstance<EventDefinitionInstance.Message>().single()
        assertThat(formMessage.reference.messageName).isEqualTo("Message_FormSubmitted")
        assertThat(event("endEvent_registrationNotPossible").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Signal("signal_registrationNotPossible", "Signal_RegistrationNotPossible"))
        assertThat(event("errorEvent_invalidMail").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Error("error_invalidMail", "Error_InvalidMail", "500"))
        assertThat(event("compensationEndEvent_registrationAborted").eventDefinitions)
            .allMatch { it is EventDefinitionInstance.Compensation }

        // boundary events carry their attachment and cancel-activity flag
        val errorBoundary = event("errorEvent_invalidMail")
        assertThat(errorBoundary.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(errorBoundary.attachedToRef).isEqualTo("subProcess_confirmation")
        assertThat(errorBoundary.interrupting).isTrue()
        val compensationBoundary = event("compensationEvent_onSubscriptionCounter")
        assertThat(compensationBoundary.attachedToRef).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(compensationBoundary.interrupting).isTrue()

        // derived timer registry
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("timer_after3Days", TimerType.DURATION, "=testVariable"),
            TimerDefinition("timer_everyDay", TimerType.DURATION, "PT1M"),
        )

        // derived compensation registry
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition(
                "compensationEndEvent_registrationAborted",
                CompensationDefinition.Type.THROWING,
                activityRef = "serviceTask_incrementSubscriptionCounter",
                waitForCompletion = false,
            ),
            CompensationDefinition(
                "compensationEvent_onSubscriptionCounter",
                CompensationDefinition.Type.CATCHING,
                activityRef = null,
                waitForCompletion = false,
            ),
        )

        // call activity target and mappings
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_abortRegistration" }
        assertThat(callActivity.hasCalledElement()).isTrue()
        assertThat(callActivity.getValue()).isEqualTo("abort-registration")
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "=subscriptionId", target = "subscriptionId"),
        )
        assertThat(callActivity.outputMappings).isEmpty()
        assertThat(callActivity.propagateAllInputVariables).isFalse()
        assertThat(callActivity.propagateAllOutputVariables).isFalse()

        // message registry — the correlation key is declared on the bpmn:Message, so it lives here and not
        // on each of the events referencing it
        assertThat(bpmnModel.definitions.messages.map { it.getValue() })
            .containsExactlyInAnyOrder("Message_FormSubmitted", "Message_SubscriptionConfirmed")
        assertThat(bpmnModel.definitions.messages.associate { it.getValue() to it.correlationKey })
            .containsEntry("Message_SubscriptionConfirmed", "=subscriptionId")

        // adjacency, resolved through the sequence flows
        assertThat(bpmnModel.graph.previousElementsOf(node("gateway_splitNotifications")))
            .containsExactly("subProcess_confirmation")
        assertThat(bpmnModel.graph.followingElementsOf(node("gateway_splitNotifications")))
            .containsExactlyInAnyOrder("serviceTask_sendWelcomeMail", "serviceTask_notifyCommunity")
        assertThat(bpmnModel.graph.attachedElementsOf(node("subProcess_confirmation")))
            .containsExactlyInAnyOrder("errorEvent_invalidMail", "timer_after3Days")
        assertThat(bpmnModel.graph.attachedElementsOf(node("serviceTask_incrementSubscriptionCounter")))
            .containsExactly("compensationEvent_onSubscriptionCounter")
        assertThat(bpmnModel.graph.attachedElementsOf(node("userTask_confirmRegistration")))
            .containsExactly("timer_everyDay")

        // root sequence flows exclude the four that belong to the confirmation sub-process
        assertThat(bpmnModel.sequenceFlows.mapNotNull { it.id })
            .doesNotContain("flow_requestToConfirmationMail", "flow_everyDayToConfirmationMail", "flow_confirmationMailToConfirm", "flow_confirmToConfirmed")
        assertThat(subProcess.sequenceFlows.mapNotNull { it.id })
            .containsExactlyInAnyOrder("flow_requestToConfirmationMail", "flow_everyDayToConfirmationMail", "flow_confirmationMailToConfirm", "flow_confirmToConfirmed")
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(15)
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isEqualTo("withApproval")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variantName).isNull()
    }

    @Test
    fun `extract captures call-activity io-mapping targets and propagate-all flags`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn"))
        val bpmnModel = underTest.read(File(resourceUrl.toURI()).readBytes())
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_abortRegistration" }
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "=subscriptionId", target = "subscriptionId"),
        )
        assertThat(callActivity.outputMappings).isEmpty()
        assertThat(callActivity.propagateAllInputVariables).isFalse()
        assertThat(callActivity.propagateAllOutputVariables).isFalse()
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.variables).containsExactlyInAnyOrder(
            VariableDefinition("test", VariableDirection.INPUT, "null"),
            VariableDefinition("authors", VariableDirection.INPUT, "=authors"),
            VariableDefinition("author", VariableDirection.INPUT, "author"),
            VariableDefinition("author", VariableDirection.OUTPUT, "=author"),
            VariableDefinition("subscribers", VariableDirection.INPUT, "=subscribers"),
            VariableDefinition("subscribers", VariableDirection.OUTPUT, "=subscribers"),
            VariableDefinition("subscriber", VariableDirection.INPUT, "subscriber"),
            VariableDefinition("results", VariableDirection.OUTPUT, "results"),
            VariableDefinition("result", VariableDirection.OUTPUT, "=result"),
            VariableDefinition("method", VariableDirection.INPUT, "POST"),
            VariableDefinition("url", VariableDirection.INPUT, "https://api.example.com/newsletter"),
            VariableDefinition("apiResponse", VariableDirection.OUTPUT, "=response"),
        )
    }

    @Test
    fun `extract classifies element-template service tasks as connectors`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c8-send-newsletter.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val implementationsByReference = bpmnModel.serviceTasks.associate { it.implementation.reference to it.implementation }
        assertThat(implementationsByReference["io.camunda:http-json:1"])
            .isInstanceOf(TaskImplementation.Connector::class.java)
        assertThat(implementationsByReference["newsletter.loadSubscribers"])
            .isInstanceOf(TaskImplementation.JobWorker::class.java)
        assertThat(implementationsByReference["newsletter.notifyAuthors"])
            .isInstanceOf(TaskImplementation.JobWorker::class.java)
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val eventSubProcess = bpmnModel.flowNodes.first { it.id == "eventSubProcess_errorHandling" }
        assertThat(eventSubProcess).isInstanceOf(FlowNodeDefinition.Activity.SubProcess::class.java)
        assertThat((eventSubProcess as FlowNodeDefinition.Activity.SubProcess).kind).isEqualTo(SubProcessKind.EVENT)

        // the event subprocess start event carries the isInterrupting flag, defaulting to true when unset
        val mailRejected = bpmnModel.allFlowNodes.first { it.id == "event_mailRejected" } as FlowNodeDefinition.Event
        assertThat(mailRejected.interrupting).isTrue()
        // a regular (non-event-subprocess) start event has no interrupting flag
        val editionCreated = bpmnModel.allFlowNodes.first { it.id == "startEvent_editionCreated" } as FlowNodeDefinition.Event
        assertThat(editionCreated.interrupting).isNull()

        // both escalation events reference the same root escalation, so the registry deduplicates to one entry
        assertThat(bpmnModel.definitions.escalations.map { it.getValue() }).containsExactly("escalation_notifySupport" to "200")
    }

    @Test
    fun `extract marks default sequence flow correctly`() {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c8-send-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())
        val bpmnModel = underTest.read(file.readBytes())

        val flowsById = bpmnModel.sequenceFlows.associateBy { it.id }
        assertThat(flowsById["flow_hasSubscribers"]).isEqualTo(
            SequenceFlowDefinition("flow_hasSubscribers", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true),
        )
        assertThat(flowsById["flow_noSubscribers"]).isEqualTo(
            SequenceFlowDefinition("flow_noSubscribers", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "=subscribers.size() > 0"),
        )
    }

    @Test
    fun `extract stays tolerant and leaves a call activity without calledElement for later validation`() {
        // given: a Camunda 7 model with a call activity and no zeebe:calledElement
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7-subscribe-newsletter.bpmn"))
        val file = File(resourceUrl.toURI())

        // when: extracting the mismatched model
        val bpmnModel = underTest.read(file.readBytes())

        // then: extraction does not validate or fail here
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_abortRegistration" }
        assertThat(callActivity.hasCalledElement()).isFalse()
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.CAMUNDA_7)
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c8-non-executable.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        val file = File(requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn")).toURI())
        val bpmnModel = underTest.read(file.readBytes())
        assertThat(bpmnModel.isExecutable).isTrue()
    }
}
