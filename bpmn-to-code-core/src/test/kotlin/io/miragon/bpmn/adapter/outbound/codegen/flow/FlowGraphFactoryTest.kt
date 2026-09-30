package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
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
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowGraphFactoryTest {

    private val leasingModel = testBikeLeasingModel()

    private val leasingGraph = FlowGraphFactory.build(leasingModel)

    private val cancellationGraph = FlowGraphFactory.build(testCancelBikeOrderModel())

    @Test
    fun `every node of every scope is a direct entry, sorted by object name`() {
        // given: seven nodes live inside subProcess_concludeContract -> they are still flat siblings of the root nodes
        assertThat(leasingGraph.nodes.map { it.propertyName })
            .contains("subProcessConcludeContract", "startEventLeasingRequestReceived", "serviceTaskValidateApplication")
            .contains("startEventCustomerEligible", "serviceTaskSendContract", "timerSignatureDeadline")
        assertThat(leasingGraph.nodes.map { it.objectName }).isSorted()
        assertThat(leasingGraph.nodes).hasSize(leasingModel.allFlowNodes.size)
    }

    @Test
    fun `sequence-flow and boundary edges are unified as target-named successors`() {
        // given: subProcess_concludeContract follows into the fork gateway and has three boundary events attached
        assertThat(leasingGraph.node("subProcessConcludeContract").successors.map { it.propertyName })
            .containsExactly("boundaryCompensateContract", "boundaryContractNotSigned", "gatewayFork", "timerSignatureReminder")

        // and: the service task follows into the credit rating and carries an error boundary
        assertThat(leasingGraph.node("serviceTaskValidateApplication").successors.map { it.propertyName })
            .containsExactly("boundaryApplicationInvalid", "businessRuleTaskCheckCreditRating")

        // boundary event is itself a node whose successor is the escape target
        assertThat(leasingGraph.node("boundaryApplicationInvalid").successors.map { it.propertyName })
            .containsExactly("gatewayCollectRejections")
    }

    @Test
    fun `subprocess points at its interior start events while interior edges stay on the interior nodes`() {
        assertThat(leasingGraph.node("subProcessConcludeContract").interiorStarts.map { it.propertyName })
            .containsExactly("startEventCustomerEligible")
        assertThat(leasingGraph.node("startEventCustomerEligible").successors.map { it.propertyName })
            .containsExactly("serviceTaskSendContract")
        assertThat(leasingGraph.node("gatewayAwaitSignature").successors.map { it.propertyName })
            .containsExactly("eventContractSigned", "timerSignatureDeadline")
    }

    @Test
    fun `nodes outside a subprocess and the root start event have no interior starts`() {
        assertThat(leasingGraph.node("startEventLeasingRequestReceived").interiorStarts).isEmpty()
        assertThat(leasingGraph.node("callActivityCancelBikeOrder").interiorStarts).isEmpty()
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
        val serviceTask = leasingGraph.node("serviceTaskValidateApplication")

        assertThat(serviceTask.id).isEqualTo("serviceTask_validateApplication")
        assertThat(serviceTask.elementType).isEqualTo("SERVICE_TASK")
        assertThat(serviceTask.eventType).isNull()
        assertThat(serviceTask.objectName).isEqualTo("ServiceTaskValidateApplication")
        assertThat(serviceTask.name).isEqualTo("Validate application")

        // the fork gateway carries no name in the model
        assertThat(leasingGraph.node("gatewayFork").name).isNull()
    }

    // --- Facets ---------------------------------------------------------------------------------------------

    @Test
    fun `service task carries its job type and directional variables`() {
        val facets = leasingGraph.node("serviceTaskSendContract").facets

        assertThat(facets.jobType?.value).isEqualTo($$"${sendContractDelegate}")
        assertThat(facets.variables.associate { it.rawName to it.subtype }).containsExactlyInAnyOrderEntriesOf(
            mapOf("applicationId" to VariableNameSubtype.INPUT, "contractId" to VariableNameSubtype.OUTPUT),
        )
        assertThat(facets.calledProcessId).isNull()
        assertThat(facets.timer).isNull()
    }

    @Test
    fun `a variable read and written by the same node is an in-out variable`() {
        val facets = leasingGraph.node("userTaskUpdateDeliveryAddress").facets

        assertThat(facets.variables).singleElement().satisfies({
            assertThat(it.constantName).isEqualTo("DELIVERY_ADDRESS")
            assertThat(it.rawName).isEqualTo("deliveryAddress")
            assertThat(it.subtype).isEqualTo(VariableNameSubtype.IN_OUT)
        })
    }

    @Test
    fun `end event with a message implementation carries the job type too`() {
        assertThat(cancellationGraph.node("endEventBikeOrderCancelled").facets.jobType?.value).isEqualTo("miravelo.bikeOrderCancelled")
        assertThat(leasingGraph.node("serviceTaskCancelContract").facets.jobType?.value).isEqualTo($$"${cancelContractDelegate}")
    }

    @Test
    fun `job type points at its shared ServiceTasks constant`() {
        assertThat(cancellationGraph.node("endEventBikeOrderCancelled").facets.jobType)
            .isEqualTo(SharedValue("miravelo.bikeOrderCancelled", SharedConstant(name = "MIRAVELO_BIKE_ORDER_CANCELLED")))
    }

    @Test
    fun `call activity carries the called process and its sorted input and output mappings`() {
        val facets = leasingGraph.node("callActivityCancelBikeOrder").facets

        assertThat(facets.calledProcessId).isEqualTo("cancelBikeOrder")
        assertThat(facets.inputs.map { it.constantName }).containsExactly("APPLICATION_ID", "ORDER_IDS")
        assertThat(facets.inputs.first().sourceExpression).isEqualTo($$"${applicationId}")
        assertThat(facets.inputs.last().source).isEqualTo("orderIds")
        assertThat(facets.outputs.map { it.target }).containsExactly("cancellationCosts")
    }

    @Test
    fun `event node carries its shape as element type and its definition as event type`() {
        val reminder = leasingGraph.node("timerSignatureReminder")

        assertThat(reminder.elementType).isEqualTo("BOUNDARY_EVENT")
        assertThat(reminder.eventType).isEqualTo("TIMER")
    }

    @Test
    fun `boundary events carry their host and whether they interrupt, timers their definition`() {
        val reminder = leasingGraph.node("timerSignatureReminder").facets
        val applicationInvalid = leasingGraph.node("boundaryApplicationInvalid").facets

        assertThat(reminder.timer).isEqualTo(TimerFacet(TimerType.DURATION, "P7D"))
        assertThat(reminder.attachedTo?.objectName).isEqualTo("SubProcessConcludeContract")
        assertThat(reminder.isInterrupting).isFalse()
        assertThat(applicationInvalid.attachedTo?.objectName).isEqualTo("ServiceTaskValidateApplication")
        assertThat(applicationInvalid.isInterrupting).isTrue()
    }

    @Test
    fun `intermediate timer carries its expression but no host`() {
        val facets = leasingGraph.node("timerWithdrawalPeriodElapsed").facets

        assertThat(facets.timer).isEqualTo(TimerFacet(TimerType.DURATION, $$"${withdrawalPeriod}"))
        assertThat(facets.attachedTo).isNull()
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

        assertThat(facets.timer).isEqualTo(TimerFacet(TimerType.CYCLE, "R/PT24H"))
        assertThat(facets.attachedTo).isNull()
        assertThat(facets.isInterrupting).isNull()
    }

    @Test
    fun `events carry their message, error and escalation references`() {
        assertThat(leasingGraph.node("startEventLeasingRequestReceived").facets.message?.value).isEqualTo("miravelo.leasingRequestReceived")
        assertThat(leasingGraph.node("boundaryApplicationInvalid").facets.error?.value).isEqualTo("miravelo.applicationInvalid" to "applicationInvalid")
        assertThat(leasingGraph.node("boundaryContractNotSigned").facets.escalation?.value).isEqualTo("miravelo.contractNotSigned" to "contractNotSigned")
        assertThat(leasingGraph.node("boundaryCompensateContract").facets.message).isNull()
    }

    @Test
    fun `event references point at their shared constants`() {
        assertThat(leasingGraph.node("startEventLeasingRequestReceived").facets.message?.constant)
            .isEqualTo(SharedConstant(name = "MIRAVELO_LEASING_REQUEST_RECEIVED"))
        assertThat(leasingGraph.node("boundaryApplicationInvalid").facets.error?.constant)
            .isEqualTo(SharedConstant(name = "MIRAVELO_APPLICATION_INVALID"))
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
        assertThat(graph.node("onError").facets.error).isEqualTo(SharedValue("Error_Inline" to "7", null))
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
                FlowNodeDefinition.Event(
                    id = "onSignal",
                    shape = EventShape.END_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Signal(signalRef = "sig_1")),
                ),
            ),
            messages = listOf(RootElementDefinition.Message(id = "msg_1", name = "Message_Registered")),
            signals = listOf(RootElementDefinition.Signal(id = "sig_1", name = "Signal_Activated")),
            escalations = listOf(RootElementDefinition.Escalation(id = "esc_1", name = "Escalation_Late", code = "42")),
        )
        val graph = FlowGraphFactory.build(model)

        assertThat(graph.node("onMessage").facets.message?.value).isEqualTo("Message_Registered")
        assertThat(graph.node("onSignal").facets.signal)
            .isEqualTo(SharedValue("Signal_Activated", SharedConstant(name = "ACTIVATED")))
        assertThat(graph.node("onEscalation").facets.escalation)
            .isEqualTo(SharedValue("Escalation_Late" to "42", SharedConstant(name = "LATE")))
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
    fun `exclusive gateway groups its outgoing flows by the elements they lead to, with label, condition and default marker`() {
        val gateway = cancellationGraph.node("gatewayCancellationPossible")

        val toMerge = gateway.outgoingFlows.single { it.target.objectName == "GatewayMergeReturn" }
        val possible = toMerge.flows.single()
        assertThat(possible.id).isEqualTo("flow_cancellationPossibleToMergeReturn")
        assertThat(possible.isDefault).isTrue()
        assertThat(possible.conditionExpression).isNull()

        val notPossible = gateway.outgoingFlows.single { it.target.objectName != "GatewayMergeReturn" }.flows.single()
        assertThat(notPossible.id).isEqualTo("flow_cancellationNotPossibleToCollectClarifications")
        assertThat(notPossible.isDefault).isFalse()
        assertThat(notPossible.conditionExpression).isEqualTo($$"${!cancellationPossible}")
        assertThat(notPossible.name).isEqualTo("No")
    }

    @Test
    fun `several flows to one target share one name but keep every flow`() {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Gateway(id = "split", kind = GatewayKind.INCLUSIVE, outgoing = listOf("flow_a", "flow_b")),
                FlowNodeDefinition.Unknown(id = "target", incoming = listOf("flow_a", "flow_b")),
            ),
            sequenceFlows = listOf(
                SequenceFlowDefinition(
                    id = "flow_a",
                    sourceRef = "split",
                    targetRef = "target",
                    conditionExpression = "=a",
                ),
                SequenceFlowDefinition(
                    id = "flow_b",
                    sourceRef = "split",
                    targetRef = "target",
                    conditionExpression = "=b",
                ),
            ),
        )
        val split = FlowGraphFactory.build(model).node("split")

        assertThat(split.successors.map { it.propertyName }).containsExactly("target")
        val toTarget = split.outgoingFlows.single()
        assertThat(toTarget.target.objectName).isEqualTo("Target")
        assertThat(toTarget.flows.map { it.conditionExpression }).containsExactly("=a", "=b")
    }

    @Test
    fun `boundary attachments are successors, marked as boundary events and never outgoing flows`() {
        val subProcess = leasingGraph.node("subProcessConcludeContract")

        assertThat(subProcess.successors.map { it.propertyName }).contains("timerSignatureReminder")
        assertThat(subProcess.outgoingFlows.map { it.target.propertyName }).containsExactly("gatewayFork")
        assertThat(leasingGraph.node("timerSignatureReminder").isBoundaryEvent).isTrue()
        assertThat(subProcess.isBoundaryEvent).isFalse()
    }

    @Test
    fun `node without sequence flows still appears with its facets`() {
        val model = testProcessModel(flowNodes = listOf(jobWorkerTask(id = "lonely", jobType = "lonely.worker")))
        val graph = FlowGraphFactory.build(model)

        assertThat(graph.nodes).hasSize(1)
        assertThat(graph.node("lonely").outgoingFlows).isEmpty()
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
