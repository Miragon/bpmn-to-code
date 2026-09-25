package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.builder.buildSubscribeNewsletterFlowNodes
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.TimerFacet
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.testSendNewsletterModel
import io.miragon.bpmn.domain.testSubscribeNewsletterModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowGraphFactoryTest {

    private val subscribeModel = testSubscribeNewsletterModel(
        flowNodes = buildSubscribeNewsletterFlowNodes(
            confirmationMailImpl = "#{sendConfirmation}",
            welcomeMailImpl = "#{sendWelcome}",
            registrationCompletedImpl = "newsletter.completed",
            notifyCommunityImpl = "newsletter.notifyCommunity",
        ),
    )

    private val subscribeGraph = FlowGraphFactory.build(subscribeModel)

    @Test
    fun `every node of every scope is a direct entry, sorted by object name`() {
        // given: five nodes live inside subProcess_confirmation -> they are still flat siblings of the root nodes
        assertThat(subscribeGraph.nodes.map { it.propertyName })
            .contains("subProcessConfirmation", "startEventSubmitRegistrationForm", "serviceTaskIncrementSubscriptionCounter")
            .contains("userTaskConfirmRegistration", "startEventRequestReceived", "timerEveryDay")
        assertThat(subscribeGraph.nodes.map { it.objectName }).isSorted()
        assertThat(subscribeGraph.nodes).hasSize(subscribeModel.allFlowNodes.size)
    }

    @Test
    fun `sequence-flow and boundary edges are unified as target-named successors`() {
        // given: subProcess_confirmation follows into the notification split gateway and has two boundary events attached
        assertThat(subscribeGraph.node("subProcessConfirmation").successors.map { it.propertyName })
            .containsExactly("errorEventInvalidMail", "gatewaySplitNotifications", "timerAfter3Days")

        // and: the service task follows into the subprocess and carries a compensation boundary
        assertThat(subscribeGraph.node("serviceTaskIncrementSubscriptionCounter").successors.map { it.propertyName })
            .containsExactly("compensationEventOnSubscriptionCounter", "subProcessConfirmation")

        // boundary event is itself a node whose successor is the escape target
        assertThat(subscribeGraph.node("errorEventInvalidMail").successors.map { it.propertyName })
            .containsExactly("endEventRegistrationNotPossible")
    }

    @Test
    fun `subprocess points at its interior start events while interior edges stay on the interior nodes`() {
        assertThat(subscribeGraph.node("subProcessConfirmation").interiorStarts.map { it.propertyName })
            .containsExactly("startEventRequestReceived")
        assertThat(subscribeGraph.node("startEventRequestReceived").isStart).isTrue()
        assertThat(subscribeGraph.node("startEventRequestReceived").successors.map { it.propertyName })
            .containsExactly("serviceTaskSendConfirmationMail")
        assertThat(subscribeGraph.node("userTaskConfirmRegistration").successors.map { it.propertyName })
            .containsExactly("endEventSubscriptionConfirmed", "timerEveryDay")
    }

    @Test
    fun `nodes outside a subprocess and the root start event have no interior starts`() {
        assertThat(subscribeGraph.node("startEventSubmitRegistrationForm").isStart).isTrue()
        assertThat(subscribeGraph.node("startEventSubmitRegistrationForm").interiorStarts).isEmpty()
        assertThat(subscribeGraph.node("callActivityAbortRegistration").interiorStarts).isEmpty()
    }

    @Test
    fun `nested subprocesses each list only their own start events`() {
        val inner = FlowNodeDefinition.Activity.SubProcess(
            id = "inner",
            kind = SubProcessKind.PLAIN,
            flowNodes = listOf(startEvent("innerStart")),
        )
        val outer = FlowNodeDefinition.Activity.SubProcess(
            id = "outer",
            kind = SubProcessKind.PLAIN,
            flowNodes = listOf(startEvent("outerStart"), inner),
        )
        val graph = FlowGraphFactory.build(testProcessModel(flowNodes = listOf(startEvent("rootStart"), outer)))

        assertThat(graph.nodes.map { it.propertyName }).containsExactly("inner", "innerStart", "outer", "outerStart", "rootStart")
        assertThat(graph.node("outer").interiorStarts.map { it.propertyName }).containsExactly("outerStart")
        assertThat(graph.node("inner").interiorStarts.map { it.propertyName }).containsExactly("innerStart")
    }

    @Test
    fun `event subprocess opens on its event start and exposes whether it interrupts`() {
        val eventSubProcess = FlowNodeDefinition.Activity.SubProcess(
            id = "errorHandling",
            kind = SubProcessKind.EVENT,
            flowNodes = listOf(
                startEvent("onError", EventDefinitionInstance.Error(errorRef = "err")).copy(interrupting = false),
                FlowNodeDefinition.Unknown(id = "handle"),
            ),
        )
        val graph = FlowGraphFactory.build(testProcessModel(flowNodes = listOf(eventSubProcess)))

        assertThat(graph.node("errorHandling").interiorStarts.map { it.propertyName }).containsExactly("onError")
        assertThat(graph.node("errorHandling").successors).isEmpty()
        assertThat(graph.node("onError").facets.isInterrupting).isFalse()
        assertThat(graph.node("onError").facets.attachedTo).isNull()
    }

    @Test
    fun `node exposes id, flat element type and optional display name`() {
        val serviceTask = subscribeGraph.node("serviceTaskIncrementSubscriptionCounter")

        assertThat(serviceTask.id).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(serviceTask.elementType).isEqualTo("SERVICE_TASK")
        assertThat(serviceTask.objectName).isEqualTo("ServiceTaskIncrementSubscriptionCounter")
        assertThat(serviceTask.name).isNull() // no displayName in the model

        // userTaskConfirmRegistration declares displayName "Confirm registration"
        assertThat(subscribeGraph.node("userTaskConfirmRegistration").name).isEqualTo("Confirm registration")
    }

    // --- Facets ---------------------------------------------------------------------------------------------

    @Test
    fun `service task carries its job type and directional variables`() {
        val facets = subscribeGraph.node("serviceTaskSendWelcomeMail").facets

        assertThat(facets.jobType?.value).isEqualTo("#{sendWelcome}")
        assertThat(facets.variables).singleElement().satisfies({
            assertThat(it.constantName).isEqualTo("SUBSCRIPTION_ID")
            assertThat(it.rawName).isEqualTo("subscriptionId")
            assertThat(it.subtype).isEqualTo(VariableNameSubtype.IN_OUT)
        })
        assertThat(facets.calledProcessId).isNull()
        assertThat(facets.timer).isNull()
    }

    @Test
    fun `end event with a job worker implementation carries the job type too`() {
        assertThat(subscribeGraph.node("endEventRegistrationCompleted").facets.jobType?.value).isEqualTo("newsletter.completed")
        assertThat(subscribeGraph.node("serviceTaskDecrementSubscriptionCounter").facets.jobType?.value).isEqualTo("counterClass")
    }

    @Test
    fun `job type points at its shared ServiceTasks constant`() {
        assertThat(subscribeGraph.node("endEventRegistrationCompleted").facets.jobType)
            .isEqualTo(SharedValue("newsletter.completed", SharedConstant(name = "NEWSLETTER_COMPLETED", rawName = "newsletter.completed")))
    }

    @Test
    fun `call activity carries the called process and its sorted input and output mappings`() {
        val facets = subscribeGraph.node("callActivityAbortRegistration").facets

        assertThat(facets.calledProcessId).isEqualTo("abort-registration")
        assertThat(facets.inputs.map { it.constantName }).containsExactly("CHILD_REASON_CODE", "CHILD_SUBSCRIPTION_ID")
        assertThat(facets.inputs.first().sourceExpression).isEqualTo("\${reasonCode}")
        assertThat(facets.inputs.last().source).isEqualTo("subscriptionId")
        assertThat(facets.outputs.map { it.target }).containsExactly("abortResult")
        assertThat(facets.variables.map { it.subtype }).containsExactly(VariableNameSubtype.INPUT)
    }

    @Test
    fun `boundary timer carries timer, host and whether it interrupts`() {
        val after3Days = subscribeGraph.node("timerAfter3Days").facets
        val everyDay = subscribeGraph.node("timerEveryDay").facets

        assertThat(after3Days.timer).isEqualTo(TimerFacet("Duration", "\${testVariable}"))
        assertThat(after3Days.attachedTo?.objectName).isEqualTo("SubProcessConfirmation")
        assertThat(after3Days.isInterrupting).isTrue()
        assertThat(everyDay.attachedTo?.objectName).isEqualTo("UserTaskConfirmRegistration")
        assertThat(everyDay.isInterrupting).isFalse()
    }

    @Test
    fun `boundary event without an explicit cancel flag interrupts by default and may hang on a call activity`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.CallActivity(
                    id = "callChild",
                    definition = CallActivityDefinition("callChild", "child"),
                    boundaryEventRefs = listOf("childTimedOut"),
                ),
                FlowNodeDefinition.Event(
                    id = "childTimedOut",
                    shape = EventShape.BOUNDARY_EVENT,
                    attachedToRef = "callChild",
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1H")),
                ),
            ),
        )
        val facets = FlowGraphFactory.build(model).node("childTimedOut").facets

        assertThat(facets.attachedTo?.objectName).isEqualTo("CallChild")
        assertThat(facets.isInterrupting).isTrue()
    }

    @Test
    fun `timer start event carries its timer but no host`() {
        val model = testProcessModel(
            flowNodes = listOf(startEvent("nightly", EventDefinitionInstance.Timer(TimerType.CYCLE, "R/PT24H"))),
        )
        val facets = FlowGraphFactory.build(model).node("nightly").facets

        assertThat(facets.timer).isEqualTo(TimerFacet("Cycle", "R/PT24H"))
        assertThat(facets.attachedTo).isNull()
        assertThat(facets.isInterrupting).isNull()
    }

    @Test
    fun `events carry their message, signal and error references`() {
        assertThat(subscribeGraph.node("startEventSubmitRegistrationForm").facets.message?.value).isEqualTo("Message_FormSubmitted")
        assertThat(subscribeGraph.node("endEventRegistrationNotPossible").facets.signal?.value).isEqualTo("Signal_RegistrationNotPossible")
        assertThat(subscribeGraph.node("errorEventInvalidMail").facets.error?.value).isEqualTo(NamedCode("Error_InvalidMail", "500"))
        assertThat(subscribeGraph.node("compensationEventOnSubscriptionCounter").facets.message).isNull()
    }

    @Test
    fun `event references point at their shared constants`() {
        assertThat(subscribeGraph.node("startEventSubmitRegistrationForm").facets.message?.constant)
            .isEqualTo(SharedConstant(name = "MESSAGE_FORM_SUBMITTED", rawName = "Message_FormSubmitted"))
        assertThat(subscribeGraph.node("endEventRegistrationNotPossible").facets.signal?.constant)
            .isEqualTo(SharedConstant(name = "SIGNAL_REGISTRATION_NOT_POSSIBLE", rawName = "Signal_RegistrationNotPossible"))
        assertThat(subscribeGraph.node("errorEventInvalidMail").facets.error?.constant)
            .isEqualTo(SharedConstant(name = "ERROR_INVALID_MAIL_500", rawName = "Error_InvalidMail_500"))
    }

    @Test
    fun `event reference without a matching root element keeps its value but has no shared constant`() {
        // given: an error event declaring name and code inline, with a ref that no root element resolves
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "onError",
                    shape = EventShape.END_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Error(errorRef = "missing", errorName = "Error_Inline", errorCode = "7")),
                ),
            ),
        )

        // when
        val graph = FlowGraphFactory.build(model)

        // then: the shared Errors file will not contain it, so the node must not reference it
        assertThat(graph.node("onError").facets.error).isEqualTo(SharedValue(NamedCode("Error_Inline", "7"), null))
    }

    @Test
    fun `event references resolve through the root-element registry by ref`() {
        val model = testProcessModel(
            flowNodes = listOf(
                startEvent("onMessage", EventDefinitionInstance.Message(io.miragon.bpmn.domain.shared.MessageReference(messageRef = "msg_1"))),
                FlowNodeDefinition.Event(
                    id = "onEscalation",
                    shape = EventShape.END_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Escalation(escalationRef = "esc_1")),
                ),
            ),
            messages = listOf(RootElementDefinition.Message(id = "msg_1", name = "Message_Registered")),
            escalations = listOf(RootElementDefinition.Escalation(id = "esc_1", name = "Escalation_Late", code = "42")),
        )
        val graph = FlowGraphFactory.build(model)

        assertThat(graph.node("onMessage").facets.message?.value).isEqualTo("Message_Registered")
        assertThat(graph.node("onEscalation").facets.escalation)
            .isEqualTo(SharedValue(NamedCode("Escalation_Late", "42"), SharedConstant(name = "ESCALATION_LATE_42", rawName = "Escalation_Late_42")))
    }

    @Test
    fun `unknown node carries only its variables`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "mystery", variables = listOf(VariableDefinition("input", VariableDirection.INPUT))),
            ),
        )
        val facets = FlowGraphFactory.build(model).node("mystery").facets

        assertThat(facets.variables.map { it.constantName }).containsExactly("INPUT")
        assertThat(facets).isEqualTo(FlowGraph.NodeFacets(variables = facets.variables))
    }

    // --- Sequence-flow edges --------------------------------------------------------------------------------

    @Test
    fun `exclusive gateway exposes one typed edge per outgoing flow with label, condition and default marker`() {
        val gateway = FlowGraphFactory.build(testSendNewsletterModel()).node("gatewayHasSubscribers")

        assertThat(gateway.flows.map { it.propertyName }).containsExactly("flowHasSubscribers", "flowNoSubscribers")
        val (hasSubscribers, noSubscribers) = gateway.flows
        assertThat(hasSubscribers.id).isEqualTo("flow_hasSubscribers")
        assertThat(hasSubscribers.isDefault).isTrue()
        assertThat(hasSubscribers.conditionExpression).isNull()
        assertThat(hasSubscribers.target.objectName).isEqualTo("ServiceTaskSendToSubscriber")
        assertThat(noSubscribers.isDefault).isFalse()
        assertThat(noSubscribers.conditionExpression).isEqualTo("\${subscribers.size() > 0}")
        assertThat(noSubscribers.name).isEqualTo("No")
    }

    @Test
    fun `parallel flows to one target stay separate edges but collapse to one successor`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.INCLUSIVE, outgoing = listOf("flow_a", "flow_b")),
                FlowNodeDefinition.Unknown(id = "target", incoming = listOf("flow_a", "flow_b")),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition("flow_a", "split", "target", conditionExpression = "=a"),
                SequenceFlowDefinition("flow_b", "split", "target", conditionExpression = "=b"),
            ),
        )
        val split = FlowGraphFactory.build(model).node("split")

        assertThat(split.successors.map { it.propertyName }).containsExactly("target")
        assertThat(split.flows.map { it.conditionExpression }).containsExactly("=a", "=b")
    }

    @Test
    fun `boundary attachments are successors but never sequence-flow edges`() {
        val subProcess = subscribeGraph.node("subProcessConfirmation")

        assertThat(subProcess.successors.map { it.propertyName }).contains("timerAfter3Days")
        assertThat(subProcess.flows.map { it.target.propertyName }).containsExactly("gatewaySplitNotifications")
    }

    @Test
    fun `node without sequence flows still appears with its facets`() {
        val model = testProcessModel(flowNodes = listOf(jobWorkerTask(id = "lonely", jobType = "lonely.worker")))
        val graph = FlowGraphFactory.build(model)

        assertThat(graph.nodes).hasSize(1)
        assertThat(graph.node("lonely").flows).isEmpty()
        assertThat(graph.node("lonely").facets.jobType?.value).isEqualTo("lonely.worker")
    }

    private fun startEvent(id: String, vararg definitions: EventDefinitionInstance) = FlowNodeDefinition.Event(
        id = id,
        shape = EventShape.START_EVENT,
        eventDefinitions = definitions.toList(),
    )

    private fun FlowGraphFactory.build(model: ProcessModel): FlowGraph = build(model.graph, model.definitions)

    private fun FlowGraph.node(propertyName: String): FlowGraphNode = nodes.single { it.propertyName == propertyName }
}
