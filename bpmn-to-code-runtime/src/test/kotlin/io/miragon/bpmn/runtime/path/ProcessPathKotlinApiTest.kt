package io.miragon.bpmn.runtime.path

import io.miragon.bpmn.runtime.BoundaryEvent
import io.miragon.bpmn.runtime.BpmnElementType
import io.miragon.bpmn.runtime.BpmnErrorDefinition
import io.miragon.bpmn.runtime.BpmnEventType
import io.miragon.bpmn.runtime.BpmnTimer
import io.miragon.bpmn.runtime.MessageName
import io.miragon.bpmn.runtime.ProcessId
import io.miragon.bpmn.runtime.TimerType
import io.miragon.bpmn.runtime.VariableName
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes.SubProcessConcludeContract
import io.miragon.bpmn.runtime.path.example.ProcessVariables
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes as BikeLeasing

/**
 * Exercises [ProcessPath] over the *actually generated* Kotlin bike-leasing API — which doubles as the compile
 * contract that the generated `: LeadsTo<…>` / `: FlowNode` code resolves against the runtime
 * interfaces. Bike leasing is the single navigation fixture and covers every element type: message start,
 * service/business-rule/receive task, embedded subprocess with interior, event-based gateway, parallel (AND)
 * split/join, call activity, boundary events (error, escalation, non-interrupting timer), terminate end, and
 * compensation.
 *
 * The `ProcessPathJavaApiTest` sibling mirrors these cases over the generated Java API; the [ProcessPathTest]
 * unit test covers each operator's mechanics in isolation over a hand-built stub graph.
 */
class ProcessPathKotlinApiTest {

    private annotation class VariableAnnotation(val name: String)

    // --- Sequential flow through a subprocess -------------------------------------------------------------

    @Test
    fun `happy path walks the subprocess interior with inside and continues checked after it`() {
        // The interior walk as a reusable, fully-checked block — typed on the subprocess so every hop compiles.
        val contractInterior: ProcessPath<SubProcessConcludeContract>.() -> ProcessPath<*> = {
            enter { it.startEventCustomerEligible }
                .then(BikeLeasing.ServiceTaskSendContract)
                .then(BikeLeasing.GatewayAwaitSignature).then(BikeLeasing.EventContractSigned).then(BikeLeasing.EndEventContractConcluded)
        }

        val path = ProcessPath.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then(BikeLeasing.ServiceTaskValidateApplication)
            .then(BikeLeasing.BusinessRuleTaskCheckCreditRating)
            .then(BikeLeasing.GatewayIsSolvent)
            .onto(BikeLeasing.SubProcessConcludeContract)
            .inside(contractInterior)
            .then(BikeLeasing.GatewayFork)
            .then(BikeLeasing.ServiceTaskOrderBike)
            .then(BikeLeasing.GatewayJoin)
            .then(BikeLeasing.ReceiveTaskHandoverReported)
            .then(BikeLeasing.TimerWithdrawalPeriodElapsed).then(BikeLeasing.EndEventLeasingActive)

        assertThat(path.ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "event_contractSigned",
            "endEvent_contractConcluded",
            "gateway_fork",
            "serviceTask_orderBike",
            "gateway_join",
            "receiveTask_handoverReported",
            "timer_withdrawalPeriodElapsed",
            "endEvent_leasingActive",
        )
    }

    // --- Boundary events ----------------------------------------------------------------------------------

    @Test
    fun `escalation boundary leaves the subprocess, entered via an explicit scope, into the terminate end`() {
        // enter(scope) descends straight into a named interior from a non-adjacent position (here after the
        // credit rating, skipping the gateway) — the re-anchor form.
        val path = ProcessPath.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then(BikeLeasing.ServiceTaskValidateApplication)
            .then(BikeLeasing.BusinessRuleTaskCheckCreditRating)
            .enter(SubProcessConcludeContract) { it.startEventCustomerEligible }
            .then(BikeLeasing.ServiceTaskSendContract)
            .then(BikeLeasing.GatewayAwaitSignature)
            .then(BikeLeasing.TimerSignatureDeadline)
            .then(BikeLeasing.EndEventContractNotSigned)
            .interruptedBy(SubProcessConcludeContract, BikeLeasing.BoundaryContractNotSigned)
            .then(BikeLeasing.GatewayCollectRejections)
            .then(BikeLeasing.ServiceTaskSendRejection).then(BikeLeasing.EndEventApplicationRejected)

        assertThat(path.ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "businessRuleTask_checkCreditRating",
            "startEvent_customerEligible",
            "serviceTask_sendContract",
            "gateway_awaitSignature",
            "timer_signatureDeadline",
            "endEvent_contractNotSigned",
            "boundary_contractNotSigned",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected",
        )
    }

    @Test
    fun `error boundary on a task is a successor of the task`() {
        val path = ProcessPath.from(BikeLeasing.StartEventLeasingRequestReceived)
            .then(BikeLeasing.ServiceTaskValidateApplication)
            .then(BikeLeasing.BoundaryApplicationInvalid)
            .then(BikeLeasing.GatewayCollectRejections)
            .then(BikeLeasing.ServiceTaskSendRejection).then(BikeLeasing.EndEventApplicationRejected)

        assertThat(path.ids).containsExactly(
            "startEvent_leasingRequestReceived",
            "serviceTask_validateApplication",
            "boundary_applicationInvalid",
            "gateway_collectRejections",
            "serviceTask_sendRejection",
            "endEvent_applicationRejected",
        )
    }

    @Test
    fun `non-interrupting timer boundary branches off the subprocess into the reminder`() {
        val path = ProcessPath.from(BikeLeasing.GatewayIsSolvent)
            .onto(BikeLeasing.SubProcessConcludeContract)
            .then(BikeLeasing.TimerSignatureReminder)
            .then(BikeLeasing.ServiceTaskSendReminderMail).then(BikeLeasing.EndEventCustomerReminded)

        assertThat(path.ids).containsExactly(
            "gateway_isSolvent",
            "timer_signatureReminder",
            "serviceTask_sendReminderMail",
            "endEvent_customerReminded",
        )
    }

    // --- Parallel (AND) branches --------------------------------------------------------------------------

    @Test
    fun `parallel branches assert as an unordered deduplicated set via nodesOf`() {
        val orderBranch = ProcessPath.from(BikeLeasing.GatewayFork)
            .then(BikeLeasing.ServiceTaskOrderBike).then(BikeLeasing.GatewayJoin).then(BikeLeasing.ReceiveTaskHandoverReported).nodes
        val insuranceBranch = ProcessPath.from(BikeLeasing.GatewayFork)
            .then(BikeLeasing.ServiceTaskIssueInsurancePolicy)
            .then(BikeLeasing.GatewayJoin).then(BikeLeasing.ReceiveTaskHandoverReported).nodes

        assertThat(nodesOf(orderBranch, insuranceBranch).map { it.id.value })
            .contains("serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join")
            .doesNotHaveDuplicates()
    }

    // --- Escape hatch -------------------------------------------------------------------------------------

    @OptIn(RiskyNavigation::class)
    @Test
    fun `jumpTo re-anchors to the fork to walk the second parallel branch in one chain`() {
        val passed = ProcessPath.from(BikeLeasing.GatewayFork)
            .then(BikeLeasing.ServiceTaskOrderBike)
            .jumpTo(BikeLeasing.GatewayFork)
            .then(BikeLeasing.ServiceTaskIssueInsurancePolicy)
            .then(BikeLeasing.GatewayJoin).then(BikeLeasing.ReceiveTaskHandoverReported).nodes

        assertThat(passed.map { it.id.value }).containsExactly(
            "gateway_fork",
            "serviceTask_orderBike",
            "serviceTask_issueInsurancePolicy",
            "gateway_join",
            "receiveTask_handoverReported",
        )
    }

    // --- Node metadata & graph boundaries -----------------------------------------------------------------

    @Test
    fun `nodes expose their id and flat elementType across element kinds`() {
        assertThat(BikeLeasing.StartEventLeasingRequestReceived.elementType).isEqualTo(BpmnElementType.START_EVENT)
        assertThat(BikeLeasing.StartEventLeasingRequestReceived.eventType).isEqualTo(BpmnEventType.MESSAGE)
        assertThat(BikeLeasing.GatewayFork.elementType).isEqualTo(BpmnElementType.PARALLEL_GATEWAY)
        assertThat(BikeLeasing.CallActivityCancelBikeOrder.elementType).isEqualTo(BpmnElementType.CALL_ACTIVITY)
        assertThat(BikeLeasing.ServiceTaskSendContract.elementType).isEqualTo(BpmnElementType.SERVICE_TASK)
        assertThat(BikeLeasing.GatewayFork.id.value).isEqualTo("gateway_fork")
    }

    @Test
    fun `nodes expose their display name, outgoing sequence flows named after their targets and their own facets`() {
        assertThat(BikeLeasing.ReceiveTaskHandoverReported.name).isEqualTo("Await bike handover")
        assertThat(BikeLeasing.GatewayFork.name).isNull()

        val flow = BikeLeasing.StartEventLeasingRequestReceived.flowsTo(BikeLeasing.ServiceTaskValidateApplication).flow
        assertThat(flow.id.value).isEqualTo("flow_leasingRequestReceivedToValidateApplication")
        assertThat(flow.target).isEqualTo(BikeLeasing.ServiceTaskValidateApplication)
        assertThat(flow.conditionExpression).isNull()
        assertThat(flow.isDefault).isFalse()
        assertThat(flow).isEqualTo(BikeLeasing.StartEventLeasingRequestReceived.flowsTo(BikeLeasing.ServiceTaskValidateApplication).flow)

        val input: VariableName.Input = BikeLeasing.ServiceTaskSendContract.Variables.APPLICATION_ID
        assertThat(input.value).isEqualTo("applicationId")
        assertThat(BikeLeasing.ServiceTaskValidateApplication.jobType).isEqualTo($$"${validateApplicationDelegate}")
        assertThat(BikeLeasing.StartEventLeasingRequestReceived.message).isEqualTo(MessageName("miravelo.leasingRequestReceived"))
        assertThat(BikeLeasing.BoundaryApplicationInvalid.error).isEqualTo(BpmnErrorDefinition("miravelo.applicationInvalid", "applicationInvalid"))

        assertThat(BikeLeasing.TimerSignatureReminder.timer).isEqualTo(BpmnTimer(TimerType.DURATION, "P7D"))
        assertThat(BikeLeasing.TimerSignatureReminder.attachedTo).isEqualTo(SubProcessConcludeContract)
        assertThat(BikeLeasing.TimerSignatureReminder.isInterrupting).isFalse()
        assertThat(BikeLeasing.TimerSignatureReminder).isInstanceOf(BoundaryEvent::class.java)
        assertThat(BikeLeasing.ReceiveTaskHandoverReported).isNotInstanceOf(BoundaryEvent::class.java)

        assertThat(BikeLeasing.CallActivityCancelBikeOrder.calledProcess).isEqualTo(ProcessId("cancelBikeOrder"))
        assertThat(BikeLeasing.CallActivityCancelBikeOrder.Inputs.ORDER_IDS.target).isEqualTo("orderIds")
        assertThat(BikeLeasing.CallActivityCancelBikeOrder.Outputs.CANCELLATION_COSTS.source).isEqualTo("cancellationCosts")
    }

    @Test
    fun `raw names are compile-time constants usable in annotations and when branches`() {
        @VariableAnnotation(name = ProcessVariables.APPLICATION_ID)
        fun describe(elementId: String): String = when (elementId) {
            BikeLeasing.ServiceTaskSendContract.ELEMENT_ID -> "send contract"
            else -> "other"
        }

        assertThat(describe("serviceTask_sendContract")).isEqualTo("send contract")
        assertThat(BikeLeasing.ServiceTaskSendContract.id.value).isEqualTo(BikeLeasing.ServiceTaskSendContract.ELEMENT_ID)
        assertThat(BikeLeasing.ServiceTaskSendContract.Variables.APPLICATION_ID.value)
            .isEqualTo(ProcessVariables.APPLICATION_ID)
    }

    @Test
    fun `then records the sequence flows it walks next to the elements`() {
        val path = ProcessPath.from(BikeLeasing.BusinessRuleTaskCheckCreditRating)
            .then(BikeLeasing.GatewayIsSolvent).then(BikeLeasing.SubProcessConcludeContract)

        assertThat(path.ids).containsExactly(
            "businessRuleTask_checkCreditRating",
            "gateway_isSolvent",
            "subProcess_concludeContract",
        )
        assertThat(path.flowIds).containsExactly("flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract")
        assertThat(BikeLeasing.GatewayIsSolvent.flowsTo(BikeLeasing.SubProcessConcludeContract).flow.isDefault).isTrue()
    }

    @Test
    fun `compensation handler is reachable only by name, not through the navigation graph`() {
        // Compensation handlers hang off a boundary event via an association, not a sequence flow, so they have
        // no incoming edge in the graph — no then/onto/enter reaches them. They stay addressable by name.
        val handler = BikeLeasing.ServiceTaskCancelContract
        assertThat(handler.id.value).isEqualTo("serviceTask_cancelContract")
        assertThat(handler.elementType).isEqualTo(BpmnElementType.SERVICE_TASK)
    }
}
