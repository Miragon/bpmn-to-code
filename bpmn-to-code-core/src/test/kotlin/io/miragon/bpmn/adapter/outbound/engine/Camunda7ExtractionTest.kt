package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.domain.ProcessModel
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
        // given: the Camunda 7 bike-leasing BPMN file from classpath
        val bpmnModel = extract("bike-leasing")

        fun node(id: String) = bpmnModel.allFlowNodes.single { it.id == id }

        // --- process-level metadata ---
        assertThat(bpmnModel.processId).isEqualTo("bikeLeasing")
        assertThat(bpmnModel.variantName).isEqualTo("corporate")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.CAMUNDA_7)
        assertThat(bpmnModel.isExecutable).isTrue()

        // --- root vs. nested scope ---
        val nestedIds = listOf(
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "event_contractSigned",
            "endEvent_contractConcluded",
            "timer_signatureDeadline",
            "endEvent_contractNotSigned",
        )
        assertThat(bpmnModel.flowNodes.map { it.id }).containsExactlyInAnyOrderElementsOf(ROOT_NODE_IDS)
        assertThat(bpmnModel.flowNodes.map { it.id }).doesNotContainAnyElementsOf(nestedIds)
        assertThat(bpmnModel.allFlowNodes.map { it.id }).containsAll(nestedIds)
        nestedIds.forEach { assertThat(bpmnModel.graph.parentIdOf(it)).isEqualTo("subProcess_concludeContract") }

        // --- sub-process: kind and children ---
        val subProcess = node("subProcess_concludeContract") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.map { it.id }).containsExactlyInAnyOrderElementsOf(nestedIds)

        // --- node kinds ---
        assertThat((node("businessRuleTask_checkCreditRating") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.BUSINESS_RULE)
        assertThat((node("userTask_updateDeliveryAddress") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.USER)
        val handover = node("receiveTask_handoverReported") as FlowNodeDefinition.Activity.Task
        assertThat(handover.kind).isEqualTo(TaskKind.RECEIVE)
        assertThat(handover.message?.messageName).isEqualTo("miravelo.handoverReported")
        assertThat((node("gateway_isSolvent") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.EXCLUSIVE)
        assertThat((node("gateway_awaitSignature") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.EVENT_BASED)
        assertThat((node("gateway_fork") as FlowNodeDefinition.Gateway).kind).isEqualTo(GatewayKind.PARALLEL)
        assertThat(node("callActivity_cancelBikeOrder")).isInstanceOf(FlowNodeDefinition.Activity.CallActivity::class.java)

        // --- service-task implementations: every Camunda 7 flavour is represented ---
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["serviceTask_validateApplication"]).isEqualTo(TaskImplementation.DelegateExpression("\${validateApplicationDelegate}"))
        assertThat(implementations["serviceTask_orderBike"]).isEqualTo(TaskImplementation.ExternalTask("miravelo.orderBike"))
        assertThat(implementations["serviceTask_issueInsurancePolicy"]).isEqualTo(TaskImplementation.JavaClass("io.miravelo.leasing.IssueInsurancePolicyDelegate"))
        assertThat(implementations["serviceTask_sendReminderMail"]).isEqualTo(TaskImplementation.Expression("\${mailService.sendReminder(applicationId)}"))
        assertThat(implementations["serviceTask_cancelContract"]).isEqualTo(TaskImplementation.DelegateExpression("\${cancelContractDelegate}"))

        // --- event definitions ---
        val applicationInvalid = node("boundary_applicationInvalid") as FlowNodeDefinition.Event
        assertThat(applicationInvalid.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(applicationInvalid.interrupting).isTrue()
        assertThat(applicationInvalid.attachedToRef).isEqualTo("serviceTask_validateApplication")
        val error = applicationInvalid.eventDefinitions.filterIsInstance<EventDefinitionInstance.Error>().single()
        assertThat(error.errorName).isEqualTo("miravelo.applicationInvalid")
        assertThat(error.errorCode).isEqualTo("applicationInvalid")
        assertThat(bpmnModel.definitions.errors.map { it.getValue() }).containsExactly("miravelo.applicationInvalid" to "applicationInvalid")

        val reminder = node("timer_signatureReminder") as FlowNodeDefinition.Event
        assertThat(reminder.shape).isEqualTo(EventShape.BOUNDARY_EVENT)
        assertThat(reminder.interrupting).isFalse()
        assertThat(reminder.attachedToRef).isEqualTo("subProcess_concludeContract")

        val requestReceived = node("startEvent_leasingRequestReceived") as FlowNodeDefinition.Event
        assertThat(requestReceived.shape).isEqualTo(EventShape.START_EVENT)
        assertThat(requestReceived.eventDefinitions.filterIsInstance<EventDefinitionInstance.Message>().single().reference.messageName)
            .isEqualTo("miravelo.leasingRequestReceived")

        val rejected = node("endEvent_applicationRejected") as FlowNodeDefinition.Event
        assertThat(rejected.shape).isEqualTo(EventShape.END_EVENT)
        assertThat(rejected.eventDefinitions).containsExactly(EventDefinitionInstance.Terminate)

        val reverseApplication = node("event_reverseApplication") as FlowNodeDefinition.Event
        assertThat(reverseApplication.shape).isEqualTo(EventShape.INTERMEDIATE_THROW_EVENT)
        assertThat(reverseApplication.eventDefinitions).allMatch { it is EventDefinitionInstance.Compensation }

        // --- derived timers ---
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("timer_signatureDeadline", TimerType.DURATION, "P14D"),
            TimerDefinition("timer_signatureReminder", TimerType.DURATION, "P7D"),
            TimerDefinition("timer_withdrawalPeriodElapsed", TimerType.DURATION, "\${withdrawalPeriod}"),
        )

        // --- derived compensations ---
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition("boundary_compensateContract", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("boundary_compensateOrder", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("boundary_compensateInsurance", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("event_reverseApplication", CompensationDefinition.Type.THROWING, activityRef = null, waitForCompletion = false),
        )

        // --- call activity (the compensation handler of the bike order) ---
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.hasCalledElement()).isTrue()
        assertThat(callActivity.getValue()).isEqualTo("cancelBikeOrder")

        // --- sequence flows: root scope vs. sub-process scope ---
        val subProcessInternalFlows = listOf(
            "flow_customerEligibleToSendContract",
            "flow_sendContractToAwaitSignature",
            "flow_awaitSignatureToContractSigned",
            "flow_contractSignedToContractConcluded",
            "flow_awaitSignatureToSignatureDeadline",
            "flow_signatureDeadlineToContractNotSigned",
        )
        assertThat(bpmnModel.sequenceFlows.map { it.id }).doesNotContainAnyElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.sequenceFlows.map { it.id }).contains("flow_isSolventToConcludeContract", "flow_concludeContractToFork")
        assertThat(subProcess.sequenceFlows.map { it.id }).containsExactlyInAnyOrderElementsOf(subProcessInternalFlows)
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(30)
        assertThat(bpmnModel.graph.allSequenceFlows).contains(
            SequenceFlowDefinition("flow_sendContractToAwaitSignature", "serviceTask_sendContract", "gateway_awaitSignature"),
            SequenceFlowDefinition("flow_signatureReminderToSendReminderMail", "timer_signatureReminder", "serviceTask_sendReminderMail"),
        )

        // --- messages registry ---
        assertThat(bpmnModel.definitions.messages.map { it.getValue() }).containsExactlyInAnyOrder(
            "miravelo.leasingRequestReceived",
            "miravelo.contractSigned",
            "miravelo.handoverReported",
            "miravelo.applicationWithdrawn",
            "miravelo.addressChanged",
        )

        // --- boundary attachments ---
        assertThat(bpmnModel.graph.attachedElementsOf(node("subProcess_concludeContract")))
            .containsExactlyInAnyOrder("boundary_compensateContract", "boundary_contractNotSigned", "timer_signatureReminder")
        assertThat(bpmnModel.graph.attachedElementsOf(node("serviceTask_validateApplication")))
            .containsExactly("boundary_applicationInvalid")

        // --- node-to-node adjacency (derived through sequence flows) ---
        assertThat(bpmnModel.graph.previousElementsOf(node("gateway_collectRejections")))
            .containsExactlyInAnyOrder("gateway_isSolvent", "boundary_applicationInvalid", "boundary_contractNotSigned")
        assertThat(bpmnModel.graph.followingElementsOf(node("gateway_fork")))
            .containsExactlyInAnyOrder("serviceTask_orderBike", "serviceTask_issueInsurancePolicy")
        assertThat(bpmnModel.graph.previousElementsOf(node("subProcess_concludeContract"))).containsExactly("gateway_isSolvent")
    }

    @Test
    fun `extract reads the membership process`() {
        // given: the Camunda 7 membership model — send task, timer cycle, signal end and compensation end
        val bpmnModel = extract("membership")

        fun event(id: String) = bpmnModel.allFlowNodes.single { it.id == id } as FlowNodeDefinition.Event

        // then
        val sendTask = bpmnModel.allFlowNodes.single { it.id == "sendTask_sendConfirmationMail" } as FlowNodeDefinition.Activity.Task
        assertThat(sendTask.kind).isEqualTo(TaskKind.SEND)
        assertThat(event("timer_resendDaily").eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.CYCLE, "R/P1D"))
        assertThat(event("timer_resendDaily").interrupting).isFalse()
        assertThat(event("timer_confirmationExpired").eventDefinitions).containsExactly(EventDefinitionInstance.Timer(TimerType.DURATION, "P3DT12H"))
        assertThat(event("endEvent_membershipActivated").eventDefinitions.filterIsInstance<EventDefinitionInstance.Signal>().single().signalName)
            .isEqualTo("miravelo.memberActivated")
        assertThat(bpmnModel.compensations).contains(
            CompensationDefinition("endEvent_membershipDeclined", CompensationDefinition.Type.THROWING, activityRef = "serviceTask_claimMembership", waitForCompletion = false),
        )
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["serviceTask_sendWelcomeMail"]).isEqualTo(TaskImplementation.Expression("\${mailService.sendWelcomeMail(email)}"))
        assertThat(implementations["serviceTask_notifyCommunity"]).isEqualTo(TaskImplementation.JavaClass("io.miravelo.membership.NotifyCommunityDelegate"))
    }

    @Test
    fun `extract reads the implementation of a message end event`() {
        val bpmnModel = extract("cancel-bike-order")
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["endEvent_bikeOrderCancelled"]).isEqualTo(TaskImplementation.ExternalTask("miravelo.bikeOrderCancelled"))
    }

    @Test
    fun `extract captures call-activity input and output mapping targets`() {
        val callActivity = extract("bike-leasing").callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "orderIds", target = "orderIds"),
        )
        assertThat(callActivity.outputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = "cancellationCosts", target = "cancellationCosts"),
        )
    }

    @Test
    fun `extract returns variantName from process-level extension properties`() {
        assertThat(extract("bike-leasing").variantName).isEqualTo("corporate")
    }

    @Test
    fun `extract returns null variantName when not specified`() {
        assertThat(extract("membership").variantName).isNull()
    }

    @Test
    fun `extract returns additionalInputVariables from camunda properties`() {
        val bpmnModel = extract("bike-leasing")
        val startEvent = bpmnModel.allFlowNodes.single { it.id == "startEvent_leasingRequestReceived" }
        assertThat(startEvent.variables).containsExactlyInAnyOrder(
            VariableDefinition("applicationId", VariableDirection.INPUT),
            VariableDefinition("bikeIds", VariableDirection.INPUT),
            VariableDefinition("monthlyNetIncome", VariableDirection.INPUT),
            VariableDefinition("age", VariableDirection.INPUT),
        )
    }

    @Test
    fun `extract preserves direction when the same variable name is both input and output on one element`() {
        val bpmnModel = extract("bike-leasing")
        val userTask = bpmnModel.allFlowNodes.single { it.id == "userTask_updateDeliveryAddress" }
        assertThat(userTask.variables).containsExactlyInAnyOrder(
            VariableDefinition("deliveryAddress", VariableDirection.INPUT, "\${deliveryAddress}"),
            VariableDefinition("deliveryAddress", VariableDirection.OUTPUT, "\${deliveryAddress}"),
        )
    }

    @Test
    fun `extract returns additionalInputVariables for non-interrupting message start event in event subprocess`() {
        val bpmnModel = extract("bike-leasing")
        // startEvent_addressChanged is nested inside an event sub-process, so it lives under allFlowNodes
        val startEvent = bpmnModel.allFlowNodes.single { it.id == "startEvent_addressChanged" }
        assertThat(startEvent.variables).containsExactlyInAnyOrder(
            VariableDefinition("street", VariableDirection.INPUT),
            VariableDefinition("city", VariableDirection.INPUT),
        )
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val bpmnModel = extract("bike-leasing")
        listOf("serviceTask_orderBike", "serviceTask_issueInsurancePolicy").forEach { id ->
            assertThat(bpmnModel.allFlowNodes.single { it.id == id }.variables).containsExactlyInAnyOrder(
                VariableDefinition("bikeIds", VariableDirection.INPUT, "\${bikeIds}"),
                VariableDefinition("bikeId", VariableDirection.INPUT, "bikeId"),
            )
        }
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val bpmnModel = extract("bike-leasing")

        listOf("subProcess_applicationWithdrawn", "subProcess_addressChanged").forEach { id ->
            val eventSubProcess = bpmnModel.flowNodes.single { it.id == id } as FlowNodeDefinition.Activity.SubProcess
            assertThat(eventSubProcess.kind).isEqualTo(SubProcessKind.EVENT)
        }

        // the event subprocess start event carries the isInterrupting flag; a regular start event has none
        fun startEvent(id: String) = bpmnModel.allFlowNodes.single { it.id == id } as FlowNodeDefinition.Event
        assertThat(startEvent("startEvent_applicationWithdrawn").interrupting).isTrue()
        assertThat(startEvent("startEvent_addressChanged").interrupting).isFalse()
        assertThat(startEvent("startEvent_leasingRequestReceived").interrupting).isNull()

        // the escalation end event and the escalation boundary event reference the same bpmn:Escalation root
        // element, so the registry — keyed by that root element — holds a single entry
        assertThat(bpmnModel.definitions.escalations.map { it.getValue() }).containsExactly("miravelo.contractNotSigned" to "contractNotSigned")
        listOf("endEvent_contractNotSigned", "boundary_contractNotSigned").forEach { id ->
            val event = bpmnModel.allFlowNodes.single { it.id == id } as FlowNodeDefinition.Event
            val escalation = event.eventDefinitions.filterIsInstance<EventDefinitionInstance.Escalation>().single()
            assertThat(escalation.escalationName).isEqualTo("miravelo.contractNotSigned")
            assertThat(escalation.escalationCode).isEqualTo("contractNotSigned")
        }
    }

    @Test
    fun `extract marks default sequence flow correctly`() {
        val flowsById = extract("bike-leasing").sequenceFlows.associateBy { it.id }
        assertThat(flowsById["flow_isSolventToConcludeContract"]).isEqualTo(
            SequenceFlowDefinition("flow_isSolventToConcludeContract", "gateway_isSolvent", "subProcess_concludeContract", flowName = "Yes", isDefault = true),
        )
        assertThat(flowsById["flow_isSolventToCollectRejections"]).isEqualTo(
            SequenceFlowDefinition("flow_isSolventToCollectRejections", "gateway_isSolvent", "gateway_collectRejections", flowName = "No", conditionExpression = "\${!solvent}"),
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
        val callActivity = extract("bike-leasing").callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.propagateAllInputVariables).isNull()
        assertThat(callActivity.propagateAllOutputVariables).isNull()
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        assertThat(extract("non-executable").isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        assertThat(extract("bike-leasing").isExecutable).isTrue()
    }

    @Test
    fun `extract treats an absent isExecutable attribute as executable`() {
        assertThat(extract("no-executable-attr").isExecutable).isTrue()
    }

    @Test
    fun `extract keeps root elements that no flow node references`() {
        // given: a model that declares a message no element points at
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:message id="message_leasingRequestReceived" name="miravelo.leasingRequestReceived" />
              <bpmn:message id="message_contractSigned" name="miravelo.contractSigned" />
              <bpmn:process id="leasingRequest" isExecutable="true">
                <bpmn:startEvent id="startEvent_leasingRequestReceived">
                  <bpmn:messageEventDefinition id="messageEventDefinition_leasingRequestReceived" messageRef="message_leasingRequestReceived" />
                </bpmn:startEvent>
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()

        // when
        val bpmnModel = underTest.read(xml.toByteArray())

        // then: the model mirrors the file rather than silently dropping the declaration —
        // UnreferencedRootElementRule is what reports it
        assertThat(bpmnModel.definitions.messages.map { it.getValue() })
            .containsExactlyInAnyOrder("miravelo.leasingRequestReceived", "miravelo.contractSigned")
        assertThat(bpmnModel.referencedDefinitionIds()).doesNotContain("message_contractSigned")
    }

    @Test
    fun `extract reads a catch-all error boundary event without errorRef`() {
        val bpmnModel = extract("catch-all-error")
        val boundaryEvent = bpmnModel.allFlowNodes.single { it.id == "boundary_anyError" } as FlowNodeDefinition.Event
        assertThat(boundaryEvent.eventDefinitions).containsExactly(EventDefinitionInstance.Error(errorRef = null, errorName = null, errorCode = null))
    }

    private fun extract(fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/c7/$fixture.bpmn"))
        return underTest.read(File(resourceUrl.toURI()).readBytes())
    }

    private companion object {
        const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"

        val ROOT_NODE_IDS = listOf(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "boundary_applicationInvalid",
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "subProcess_concludeContract",
            "boundary_compensateContract",
            "boundary_contractNotSigned",
            "timer_signatureReminder",
            "serviceTask_sendReminderMail",
            "endEvent_customerReminded",
            "serviceTask_cancelContract",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected",
            "gateway_fork",
            "serviceTask_orderBike",
            "boundary_compensateOrder",
            "callActivity_cancelBikeOrder",
            "serviceTask_issueInsurancePolicy",
            "boundary_compensateInsurance",
            "serviceTask_cancelPolicy",
            "gateway_join",
            "receiveTask_handoverReported",
            "timer_withdrawalPeriodElapsed",
            "endEvent_leasingActive",
            "subProcess_applicationWithdrawn",
            "subProcess_addressChanged",
        )
    }
}
