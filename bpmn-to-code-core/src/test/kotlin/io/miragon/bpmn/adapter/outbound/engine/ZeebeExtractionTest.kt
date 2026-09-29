package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.CallActivityDefinition
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
        // given: the Camunda 8 / Zeebe bike-leasing BPMN file from classpath
        val bpmnModel = extract("zeebe/bike-leasing")

        fun node(id: String): FlowNodeDefinition = bpmnModel.allFlowNodes.single { it.id == id }
        fun event(id: String): FlowNodeDefinition.Event = node(id) as FlowNodeDefinition.Event

        // process-level metadata
        assertThat(bpmnModel.processId).isEqualTo("bikeLeasing")
        assertThat(bpmnModel.variantName).isEqualTo("corporate")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.ZEEBE)
        assertThat(bpmnModel.isExecutable).isTrue()

        // the contract sub-process nests its children and reports them through the flat view with the right parent
        val nestedIds = listOf(
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "event_contractSigned",
            "endEvent_contractConcluded",
            "timer_signatureDeadline",
            "endEvent_contractNotSigned",
        )
        val subProcess = node("subProcess_concludeContract") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.mapNotNull { it.id }).containsExactlyInAnyOrderElementsOf(nestedIds)
        assertThat(bpmnModel.flowNodes.mapNotNull { it.id }).doesNotContainAnyElementsOf(nestedIds)
        nestedIds.forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("subProcess_concludeContract") }
        assertThat(bpmnModel.graph.parentIdOf("callActivity_cancelBikeOrder")).isNull()

        // node kinds
        assertThat((node("gateway_fork") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat((node("gateway_awaitSignature") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.EVENT_BASED)
        val creditRating = node("businessRuleTask_checkCreditRating") as FlowNodeDefinition.Activity.Task
        assertThat(creditRating.kind).isEqualTo(TaskKind.BUSINESS_RULE)
        // a DMN-backed business rule task has no zeebe:taskDefinition, so no job type either
        assertThat(creditRating.implementation).isNull()

        // the receive task references its message directly (not through an event definition)
        val handover = node("receiveTask_handoverReported") as FlowNodeDefinition.Activity.Task
        assertThat(handover.kind).isEqualTo(TaskKind.RECEIVE)
        assertThat(handover.message?.messageName).isEqualTo("miravelo.handoverReported")

        // service-task-like implementations are Zeebe job workers, the element-templated one a connector
        val implementationsById = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementationsById["serviceTask_validateApplication"])
            .isEqualTo(TaskImplementation.JobWorker("miravelo.validateApplication"))
        assertThat(implementationsById["serviceTask_sendContract"])
            .isEqualTo(TaskImplementation.JobWorker("miravelo.sendContract"))
        assertThat(implementationsById["serviceTask_cancelContract"])
            .isEqualTo(TaskImplementation.JobWorker("miravelo.cancelContract"))
        assertThat(implementationsById["serviceTask_orderBike"])
            .isEqualTo(TaskImplementation.Connector("io.camunda:http-json:1", "io.camunda.connectors.HttpJson.v2"))

        // event definitions
        assertThat(event("timer_signatureReminder").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "P7D"))
        val requestMessage = event("startEvent_leasingRequestReceived").eventDefinitions
            .filterIsInstance<EventDefinitionInstance.Message>().single()
        assertThat(requestMessage.reference.messageName).isEqualTo("miravelo.leasingRequestReceived")
        assertThat(event("boundary_applicationInvalid").eventDefinitions).containsExactly(
            EventDefinitionInstance.Error(errorRef = "error_applicationInvalid", errorName = "miravelo.applicationInvalid", errorCode = "applicationInvalid"),
        )
        assertThat(event("endEvent_applicationRejected").eventDefinitions).containsExactly(EventDefinitionInstance.Terminate)

        // boundary events carry their attachment and cancel-activity flag
        val reminder = event("timer_signatureReminder")
        assertThat(reminder.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(reminder.attachedToRef).isEqualTo("subProcess_concludeContract")
        assertThat(reminder.interrupting).isFalse()
        val compensateOrder = event("boundary_compensateOrder")
        assertThat(compensateOrder.attachedToRef).isEqualTo("serviceTask_orderBike")
        assertThat(compensateOrder.interrupting).isTrue()

        // derived timer registry
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition(id = "timer_signatureDeadline", type = TimerType.DURATION, expression = "P14D"),
            TimerDefinition(id = "timer_signatureReminder", type = TimerType.DURATION, expression = "P7D"),
            TimerDefinition(
                id = "timer_withdrawalPeriodElapsed",
                type = TimerType.DURATION,
                expression = "=withdrawalPeriod",
            ),
        )

        // compensation event definitions, per node
        val compensations = bpmnModel.allFlowNodes.filterIsInstance<FlowNodeDefinition.Event>()
            .flatMap { node -> node.eventDefinitions.filterIsInstance<EventDefinitionInstance.Compensation>().map { node.id to it } }
        assertThat(compensations).containsExactlyInAnyOrder(
            "boundary_compensateContract" to EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false),
            "boundary_compensateOrder" to EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false),
            "boundary_compensateInsurance" to EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false),
            "event_reverseApplication" to EventDefinitionInstance.Compensation(activityRef = null, waitForCompletion = false),
        )

        // call activity target and mappings
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.hasCalledElement()).isTrue()
        assertThat(callActivity.calledElement).isEqualTo("cancelBikeOrder")

        // message registry — the correlation key is declared on the bpmn:Message, so it lives here and not
        // on each of the elements referencing it
        assertThat(bpmnModel.definitions.messages.map { it.getValue() }).containsExactlyInAnyOrder(
            "miravelo.leasingRequestReceived",
            "miravelo.contractSigned",
            "miravelo.handoverReported",
            "miravelo.applicationWithdrawn",
            "miravelo.addressChanged",
        )
        assertThat(bpmnModel.definitions.messages.associate { it.getValue() to it.correlationKey })
            .containsEntry("miravelo.contractSigned", "=applicationId")
            .containsEntry("miravelo.leasingRequestReceived", null)

        // adjacency, resolved through the sequence flows
        assertThat(bpmnModel.graph.previousElementsOf(node("gateway_fork")))
            .containsExactly("subProcess_concludeContract")
        assertThat(bpmnModel.graph.followingElementsOf(node("gateway_awaitSignature")))
            .containsExactlyInAnyOrder("event_contractSigned", "timer_signatureDeadline")
        assertThat(bpmnModel.graph.attachedElementsOf(node("subProcess_concludeContract")))
            .containsExactlyInAnyOrder("boundary_compensateContract", "boundary_contractNotSigned", "timer_signatureReminder")

        // root sequence flows exclude the six that belong to the contract sub-process
        assertThat(subProcess.sequenceFlows.mapNotNull { it.id }).hasSize(6)
        assertThat(bpmnModel.sequenceFlows.mapNotNull { it.id }).doesNotContainAnyElementsOf(subProcess.sequenceFlows.mapNotNull { it.id })
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(30)
    }

    @Test
    fun `extract reads the membership process`() {
        // given: the Zeebe membership model
        val bpmnModel = extract("zeebe/membership")

        fun event(id: String) = bpmnModel.allFlowNodes.single { it.id == id } as FlowNodeDefinition.Event

        // then: unlike Camunda 7, Zeebe reads the job type of a send task like any other task definition
        val sendTask = bpmnModel.allFlowNodes.single { it.id == "sendTask_sendConfirmationMail" } as FlowNodeDefinition.Activity.Task
        assertThat(sendTask.kind).isEqualTo(TaskKind.SEND)
        assertThat(sendTask.implementation).isEqualTo(TaskImplementation.JobWorker("miravelo.sendConfirmationMail"))
        assertThat(event("timer_resendDaily").eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.CYCLE, "R/P1D"))
        assertThat(event("endEvent_membershipActivated").eventDefinitions)
            .containsExactly(EventDefinitionInstance.Signal(signalRef = "signal_memberActivated", signalName = "miravelo.memberActivated"))
        assertThat(bpmnModel.definitions.messages.associate { it.getValue() to it.correlationKey })
            .containsEntry("miravelo.confirmationRejected", "=membershipId")
    }

    @Test
    fun `extract reads the job type of a message end event`() {
        val implementations = extract("zeebe/cancel-bike-order").serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["endEvent_bikeOrderCancelled"]).isEqualTo(TaskImplementation.JobWorker("miravelo.bikeOrderCancelled"))
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        assertThat(extract("zeebe/bike-leasing").variantName).isEqualTo("corporate")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        assertThat(extract("zeebe/membership").variantName).isNull()
    }

    @Test
    fun `extract captures call-activity io-mapping targets and propagate-all flags`() {
        val callActivity = extract("zeebe/bike-leasing").callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(
                direction = VariableDirection.INPUT,
                source = "=orderIds",
                target = "orderIds",
            ),
            CallActivityDefinition.Mapping(
                direction = VariableDirection.INPUT,
                source = "=applicationId",
                target = "applicationId",
            ),
        )
        assertThat(callActivity.outputMappings).containsExactly(
            CallActivityDefinition.Mapping(
                direction = VariableDirection.OUTPUT,
                source = "=cancellationCosts",
                target = "cancellationCosts",
            ),
        )
        assertThat(callActivity.propagateAllInputVariables).isFalse()
        assertThat(callActivity.propagateAllOutputVariables).isFalse()
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val bpmnModel = extract("zeebe/bike-leasing")
        assertThat(bpmnModel.allFlowNodes.single { it.id == "serviceTask_orderBike" }.variables).containsExactlyInAnyOrder(
            VariableDefinition(
                name = "authentication.type",
                direction = VariableDirection.INPUT,
                valueExpression = "noAuth",
            ),
            VariableDefinition(name = "method", direction = VariableDirection.INPUT, valueExpression = "POST"),
            VariableDefinition(
                name = "url",
                direction = VariableDirection.INPUT,
                valueExpression = "https://supplier.miravelo.example/orders",
            ),
            VariableDefinition(
                name = "body",
                direction = VariableDirection.INPUT,
                valueExpression = "={bikeId: bikeId}",
            ),
            VariableDefinition(
                name = "orderId",
                direction = VariableDirection.OUTPUT,
                valueExpression = "=response.body.orderId",
            ),
            VariableDefinition(name = "bikeId", direction = VariableDirection.INPUT, valueExpression = "bikeId"),
            VariableDefinition(name = "bikeIds", direction = VariableDirection.INPUT, valueExpression = "=bikeIds"),
            VariableDefinition(name = "orderId", direction = VariableDirection.OUTPUT, valueExpression = "=orderId"),
            VariableDefinition(name = "orderIds", direction = VariableDirection.OUTPUT, valueExpression = "orderIds"),
        )
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val bpmnModel = extract("zeebe/bike-leasing")

        val eventSubProcess = bpmnModel.flowNodes.single { it.id == "subProcess_applicationWithdrawn" }
        assertThat((eventSubProcess as FlowNodeDefinition.Activity.SubProcess).kind).isEqualTo(SubProcessKind.EVENT)

        // the event subprocess start event carries the isInterrupting flag, defaulting to true when unset
        val withdrawn = bpmnModel.allFlowNodes.single { it.id == "startEvent_applicationWithdrawn" } as FlowNodeDefinition.Event
        assertThat(withdrawn.interrupting).isTrue()
        val addressChanged = bpmnModel.allFlowNodes.single { it.id == "startEvent_addressChanged" } as FlowNodeDefinition.Event
        assertThat(addressChanged.interrupting).isFalse()
        // a regular (non-event-subprocess) start event has no interrupting flag
        val requestReceived = bpmnModel.allFlowNodes.single { it.id == "startEvent_leasingRequestReceived" } as FlowNodeDefinition.Event
        assertThat(requestReceived.interrupting).isNull()

        // the escalation end and boundary events reference the same root escalation, so the registry deduplicates to one entry
        assertThat(bpmnModel.definitions.escalations.map { it.getValue() }).containsExactly("miravelo.contractNotSigned" to "contractNotSigned")
    }

    @Test
    fun `extract marks default sequence flow correctly`() {
        val flowsById = extract("zeebe/bike-leasing").sequenceFlows.associateBy { it.id }
        assertThat(flowsById["flow_isSolventToConcludeContract"]).isEqualTo(
            SequenceFlowDefinition(
                id = "flow_isSolventToConcludeContract",
                sourceRef = "gateway_isSolvent",
                targetRef = "subProcess_concludeContract",
                flowName = "Yes",
                isDefault = true,
            ),
        )
        assertThat(flowsById["flow_isSolventToCollectRejections"]).isEqualTo(
            SequenceFlowDefinition(
                id = "flow_isSolventToCollectRejections",
                sourceRef = "gateway_isSolvent",
                targetRef = "gateway_collectRejections",
                flowName = "No",
                conditionExpression = "=not(solvent)",
            ),
        )
    }

    @Test
    fun `extract stays tolerant and leaves a call activity without calledElement for later validation`() {
        // given: a Camunda 7 model with a call activity and no zeebe:calledElement
        val bpmnModel = extract("c7/bike-leasing")

        // then: extraction does not validate or fail here
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.hasCalledElement()).isFalse()
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.CAMUNDA_7)
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        assertThat(extract("zeebe/non-executable").isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        assertThat(extract("zeebe/bike-leasing").isExecutable).isTrue()
    }

    @Test
    fun `extract reads a catch-all error boundary event without errorRef`() {
        val bpmnModel = extract("zeebe/cancel-bike-order")
        val boundaryEvent = bpmnModel.allFlowNodes.single { it.id == "boundary_cancellationFailed" } as FlowNodeDefinition.Event
        assertThat(boundaryEvent.eventDefinitions).containsExactly(EventDefinitionInstance.Error(errorRef = null, errorName = null, errorCode = null))
    }

    private fun extract(fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/$fixture.bpmn"))
        return underTest.read(File(resourceUrl.toURI()).readBytes())
    }
}
