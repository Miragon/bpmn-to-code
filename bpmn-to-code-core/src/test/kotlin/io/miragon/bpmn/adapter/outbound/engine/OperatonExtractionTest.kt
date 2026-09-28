package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.CompensationDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
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
        // given: the Operaton bike-leasing BPMN file from classpath
        val bpmnModel = extract("bike-leasing")

        fun node(id: String) = bpmnModel.allFlowNodes.single { it.id == id }

        // --- process-level metadata ---
        assertThat(bpmnModel.processId).isEqualTo("bikeLeasing")
        assertThat(bpmnModel.variantName).isEqualTo("corporate")
        assertThat(bpmnModel.detectedEngine).isEqualTo(ProcessEngine.OPERATON)
        assertThat(bpmnModel.isExecutable).isTrue()

        // --- the contract sub-process owns its children ---
        val subProcess = node("subProcess_concludeContract") as FlowNodeDefinition.Activity.SubProcess
        assertThat(subProcess.kind).isEqualTo(SubProcessKind.PLAIN)
        assertThat(subProcess.flowNodes.map { it.id }).containsExactlyInAnyOrder(
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "event_contractSigned",
            "endEvent_contractConcluded",
            "timer_signatureDeadline",
            "endEvent_contractNotSigned",
        )
        assertThat(bpmnModel.graph.allSequenceFlows).hasSize(30)

        // --- the operaton namespace carries the identical implementation vocabulary (ADR 010) ---
        val implementations = bpmnModel.serviceTasks.associate { it.id to it.implementation }
        assertThat(implementations["serviceTask_validateApplication"]).isEqualTo(TaskImplementation.DelegateExpression("\${validateApplicationDelegate}"))
        assertThat(implementations["serviceTask_orderBike"]).isEqualTo(TaskImplementation.ExternalTask("miravelo.orderBike"))
        assertThat(implementations["serviceTask_issueInsurancePolicy"]).isEqualTo(TaskImplementation.JavaClass("io.miravelo.leasing.IssueInsurancePolicyDelegate"))
        assertThat(implementations["serviceTask_sendReminderMail"]).isEqualTo(TaskImplementation.Expression("\${mailService.sendReminder(applicationId)}"))
        assertThat((node("businessRuleTask_checkCreditRating") as FlowNodeDefinition.Activity.Task).kind).isEqualTo(TaskKind.BUSINESS_RULE)
        assertThat((node("receiveTask_handoverReported") as FlowNodeDefinition.Activity.Task).message?.messageName)
            .isEqualTo("miravelo.handoverReported")

        // --- events ---
        val error = (node("boundary_applicationInvalid") as FlowNodeDefinition.Event).eventDefinitions
            .filterIsInstance<EventDefinitionInstance.Error>().single()
        assertThat(error.errorName to error.errorCode).isEqualTo("miravelo.applicationInvalid" to "applicationInvalid")
        assertThat((node("endEvent_applicationRejected") as FlowNodeDefinition.Event).eventDefinitions)
            .containsExactly(EventDefinitionInstance.Terminate)
        assertThat(bpmnModel.timers).containsExactlyInAnyOrder(
            TimerDefinition("timer_signatureDeadline", TimerType.DURATION, "P14D"),
            TimerDefinition("timer_signatureReminder", TimerType.DURATION, "P7D"),
            TimerDefinition("timer_withdrawalPeriodElapsed", TimerType.DURATION, "\${withdrawalPeriod}"),
        )
        assertThat(bpmnModel.compensations).containsExactlyInAnyOrder(
            CompensationDefinition("boundary_compensateContract", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("boundary_compensateOrder", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("boundary_compensateInsurance", CompensationDefinition.Type.CATCHING, activityRef = null, waitForCompletion = false),
            CompensationDefinition("event_reverseApplication", CompensationDefinition.Type.THROWING, activityRef = null, waitForCompletion = false),
        )

        // --- call activity ---
        val callActivity = bpmnModel.callActivities.single { it.id == "callActivity_cancelBikeOrder" }
        assertThat(callActivity.getValue()).isEqualTo("cancelBikeOrder")
        assertThat(callActivity.inputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.INPUT, source = "orderIds", target = "orderIds"),
        )
        assertThat(callActivity.outputMappings).containsExactly(
            CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = "cancellationCosts", target = "cancellationCosts"),
        )

        // --- adjacency ---
        assertThat(bpmnModel.graph.previousElementsOf(node("gateway_collectRejections")))
            .containsExactlyInAnyOrder("gateway_isSolvent", "boundary_applicationInvalid", "boundary_contractNotSigned")
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
    fun `extract returns additionalInputVariables from operaton properties`() {
        val bpmnModel = extract("bike-leasing")
        assertThat(bpmnModel.allFlowNodes.single { it.id == "startEvent_addressChanged" }.variables).containsExactlyInAnyOrder(
            VariableDefinition("street", VariableDirection.INPUT),
            VariableDefinition("city", VariableDirection.INPUT),
        )
    }

    @Test
    fun `extract preserves direction when the same variable name is both input and output on one element`() {
        val bpmnModel = extract("bike-leasing")
        assertThat(bpmnModel.allFlowNodes.single { it.id == "userTask_updateDeliveryAddress" }.variables).containsExactlyInAnyOrder(
            VariableDefinition("deliveryAddress", VariableDirection.INPUT, "\${deliveryAddress}"),
            VariableDefinition("deliveryAddress", VariableDirection.OUTPUT, "\${deliveryAddress}"),
        )
    }

    @Test
    fun `extract returns multi-instance variables`() {
        val bpmnModel = extract("bike-leasing")
        assertThat(bpmnModel.allFlowNodes.single { it.id == "serviceTask_orderBike" }.variables).containsExactlyInAnyOrder(
            VariableDefinition("bikeIds", VariableDirection.INPUT, "\${bikeIds}"),
            VariableDefinition("bikeId", VariableDirection.INPUT, "bikeId"),
        )
    }

    @Test
    fun `extract detects event subprocess type and extracts escalations`() {
        val bpmnModel = extract("bike-leasing")

        val eventSubProcess = bpmnModel.flowNodes.single { it.id == "subProcess_addressChanged" } as FlowNodeDefinition.Activity.SubProcess
        assertThat(eventSubProcess.kind).isEqualTo(SubProcessKind.EVENT)
        val addressChanged = bpmnModel.allFlowNodes.single { it.id == "startEvent_addressChanged" } as FlowNodeDefinition.Event
        assertThat(addressChanged.interrupting).isFalse()

        assertThat(bpmnModel.definitions.escalations.map { it.getValue() }).containsExactly("miravelo.contractNotSigned" to "contractNotSigned")
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
    fun `extract reads the membership and welcome-package signal pair`() {
        val activated = extract("membership").allFlowNodes.single { it.id == "endEvent_membershipActivated" } as FlowNodeDefinition.Event
        val memberActivated = extract("welcome-package").allFlowNodes.single { it.id == "startEvent_memberActivated" } as FlowNodeDefinition.Event
        listOf(activated, memberActivated).forEach { event ->
            assertThat(event.eventDefinitions.filterIsInstance<EventDefinitionInstance.Signal>().single().signalName)
                .isEqualTo("miravelo.memberActivated")
        }
    }

    @Test
    fun `extract marks a process with isExecutable false as non-executable`() {
        assertThat(extract("non-executable").isExecutable).isFalse()
    }

    @Test
    fun `extract marks a process with isExecutable true as executable`() {
        assertThat(extract("bike-leasing").isExecutable).isTrue()
    }

    private fun extract(fixture: String): ProcessModel {
        val resourceUrl = requireNotNull(javaClass.getResource("/bpmn/operaton/$fixture.bpmn"))
        return underTest.read(File(resourceUrl.toURI()).readBytes())
    }

    private companion object {
        const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"
    }
}
