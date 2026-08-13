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
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

        // the sub-process nests its children and reports them through the flat view with the right parent
        val subProcess = node("SubProcess_Confirmation") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.mapNotNull { it.id }).containsExactlyInAnyOrder(
            "Activity_ConfirmRegistration",
            "Activity_SendConfirmationMail",
            "EndEvent_SubscriptionConfirmed",
            "StartEvent_RequestReceived",
            "Timer_EveryDay",
        )
        listOf(
            "Activity_ConfirmRegistration",
            "Activity_SendConfirmationMail",
            "EndEvent_SubscriptionConfirmed",
            "StartEvent_RequestReceived",
            "Timer_EveryDay",
        ).forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("SubProcess_Confirmation") }
        assertThat(bpmnModel.graph.parentIdOf("CallActivity_AbortRegistration")).isNull()

        // node kinds
        assertThat((node("Gateway_SplitNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat((node("Gateway_JoinNotifications") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        val compensationHandler = node("CompensationTask_DecrementSubscriptionCounter") as FlowNodeDefinition.Activity.Task
        assertThat(compensationHandler.kind).isEqualTo(TaskKind.SERVICE)
        // a serviceTask, but the Zeebe fixture configures no zeebe:taskDefinition for it
        assertThat(compensationHandler.implementation).isNull()

        // the receive task references its message directly (not through an event definition)
        val confirmRegistration = node("Activity_ConfirmRegistration") as FlowNodeDefinition.Activity.Task
        assertThat(confirmRegistration.kind).isEqualTo(TaskKind.RECEIVE)
        assertThat(confirmRegistration.message?.messageName).isEqualTo("Message_SubscriptionConfirmed")

        // service-task-like implementations are all Zeebe job workers
        val implementationsById = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementationsById["Activity_SendConfirmationMail"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.sendConfirmationMail"))
        assertThat(implementationsById["Activity_SendWelcomeMail"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.sendWelcomeMail"))
        assertThat(implementationsById["Activity_NotifyCommunity"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.notifyCommunity"))
        assertThat(implementationsById["serviceTask_incrementSubscriptionCounter"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.incrementCounter"))
        assertThat(implementationsById["EndEvent_RegistrationCompleted"])
            .isEqualTo(TaskImplementation.JobWorker("newsletter.registrationCompleted"))

        // event definitions
        assertThat(event("Timer_After3Days").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "=testVariable"))
        assertThat(event("Timer_EveryDay").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M"))
        val formMessage = event("StartEvent_SubmitRegistrationForm").eventDefinitions
            .filterIsInstance<EventDefinitionInstance.Message>().single()
        assertThat(formMessage.reference.messageName).isEqualTo("Message_FormSubmitted")
        assertThat(event("EndEvent_RegistrationNotPossible").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Signal("Signal_14g8ki5", "Signal_RegistrationNotPossible"))
        assertThat(event("ErrorEvent_InvalidMail").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Error("Error_0uxgmyc", "Error_InvalidMail", "500"))
        assertThat(event("CompensationEndEvent_RegistrationAborted").eventDefinitions)
            .allMatch { it is EventDefinitionInstance.Compensation }

        // boundary events carry their attachment and cancel-activity flag
        val errorBoundary = event("ErrorEvent_InvalidMail")
        assertThat(errorBoundary.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(errorBoundary.attachedToRef).isEqualTo("SubProcess_Confirmation")
        assertThat(errorBoundary.interrupting).isTrue()
        val compensationBoundary = event("CompensationEvent_OnSubscriptionCounter")
        assertThat(compensationBoundary.attachedToRef).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(compensationBoundary.interrupting).isTrue()

        // derived timer registry
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("Timer_After3Days", TimerType.DURATION, "=testVariable"),
            TimerDefinition("Timer_EveryDay", TimerType.DURATION, "PT1M"),
        )

        // derived compensation registry
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition(
                "CompensationEndEvent_RegistrationAborted",
                CompensationDefinition.Type.THROWING,
                activityRef = "serviceTask_incrementSubscriptionCounter",
                waitForCompletion = false,
            ),
            CompensationDefinition(
                "CompensationEvent_OnSubscriptionCounter",
                CompensationDefinition.Type.CATCHING,
                activityRef = null,
                waitForCompletion = false,
            ),
        )

        // call activity target and mappings
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
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
        assertThat(bpmnModel.graph.previousElementsOf(node("Gateway_SplitNotifications")))
            .containsExactly("SubProcess_Confirmation")
        assertThat(bpmnModel.graph.followingElementsOf(node("Gateway_SplitNotifications")))
            .containsExactlyInAnyOrder("Activity_SendWelcomeMail", "Activity_NotifyCommunity")
        assertThat(bpmnModel.graph.attachedElementsOf(node("SubProcess_Confirmation")))
            .containsExactlyInAnyOrder("ErrorEvent_InvalidMail", "Timer_After3Days")
        assertThat(bpmnModel.graph.attachedElementsOf(node("serviceTask_incrementSubscriptionCounter")))
            .containsExactly("CompensationEvent_OnSubscriptionCounter")
        assertThat(bpmnModel.graph.attachedElementsOf(node("Activity_ConfirmRegistration")))
            .containsExactly("Timer_EveryDay")

        // root sequence flows exclude the four that belong to the confirmation sub-process
        assertThat(bpmnModel.sequenceFlows.mapNotNull { it.id })
            .doesNotContain("Flow_05i3x1y", "Flow_0x4ewvb", "Flow_1bckm43", "Flow_1cpwe57")
        assertThat(subProcess.sequenceFlows.mapNotNull { it.id })
            .containsExactlyInAnyOrder("Flow_05i3x1y", "Flow_0x4ewvb", "Flow_1bckm43", "Flow_1cpwe57")
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
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
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
        assertThat(flowsById["Flow_1jogut0"]).isEqualTo(
            SequenceFlowDefinition("Flow_1jogut0", "gateway_hasSubscribers", "serviceTask_sendToSubscriber", flowName = "Yes", isDefault = true)
        )
        assertThat(flowsById["Flow_1gsz7wd"]).isEqualTo(
            SequenceFlowDefinition("Flow_1gsz7wd", "gateway_hasSubscribers", "endEvent_noSubscribers", flowName = "No", conditionExpression = "=subscribers.size() > 0")
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
        val callActivity = bpmnModel.callActivities.single { it.id == "CallActivity_AbortRegistration" }
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
