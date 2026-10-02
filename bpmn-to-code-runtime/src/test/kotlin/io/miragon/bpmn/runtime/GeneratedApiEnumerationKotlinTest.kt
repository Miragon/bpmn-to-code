package io.miragon.bpmn.runtime

import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes
import io.miragon.bpmn.runtime.path.example.Errors
import io.miragon.bpmn.runtime.path.example.Escalations
import io.miragon.bpmn.runtime.path.example.Messages
import io.miragon.bpmn.runtime.path.example.ServiceTasks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Enumerates the generated bike-leasing API from **Kotlin**: `FlowNodes.all`, the shared definitions' `all` and a
 * node's `variables`. The `GeneratedApiEnumerationJavaTest` sibling covers the Java `all()` accessors.
 */
class GeneratedApiEnumerationKotlinTest {

    @Test
    fun `flow nodes list exactly the nodes of the process`() {
        val everyNode = FlowNodes::class.nestedClasses.map { it.objectInstance }.filterIsInstance<FlowNode>()

        assertThat(FlowNodes.all).hasSize(42).containsExactlyInAnyOrderElementsOf(everyNode)
    }

    @Test
    fun `facet interfaces filter the nodes by the facet a node carries`() {
        val timers = FlowNodes.all.filterIsInstance<TimerEvent>()

        assertThat(timers).containsExactly(
            FlowNodes.TimerSignatureDeadline,
            FlowNodes.TimerSignatureReminder,
            FlowNodes.TimerWithdrawalPeriodElapsed,
        )
        assertThat(timers.map { it.timer.timerValue }).containsExactly("P14D", "P7D", $$"${withdrawalPeriod}")
    }

    @Test
    fun `shared definitions list exactly their values in declaration order`() {
        assertThat(ServiceTasks.all).containsExactly(
            ServiceTasks.CANCEL_CONTRACT_DELEGATE,
            ServiceTasks.MAIL_SERVICE_SEND_REMINDER_APPLICATION_ID_,
            ServiceTasks.SEND_CONTRACT_DELEGATE,
            ServiceTasks.VALIDATE_APPLICATION_DELEGATE,
            ServiceTasks.IO_MIRAVELO_LEASING_ISSUE_INSURANCE_POLICY_DELEGATE,
            ServiceTasks.MIRAVELO_CANCEL_POLICY,
            ServiceTasks.MIRAVELO_ORDER_BIKE,
            ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION,
            ServiceTasks.MIRAVELO_SEND_REJECTION,
        )
        assertThat(Messages.all).containsExactly(
            Messages.MIRAVELO_ADDRESS_CHANGED,
            Messages.MIRAVELO_APPLICATION_WITHDRAWN,
            Messages.MIRAVELO_CONTRACT_SIGNED,
            Messages.MIRAVELO_HANDOVER_REPORTED,
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED,
        )
        assertThat(Errors.all).containsExactly(Errors.MIRAVELO_APPLICATION_INVALID)
        assertThat(Escalations.all).containsExactly(Escalations.MIRAVELO_CONTRACT_NOT_SIGNED)
    }

    @Test
    fun `a node lists the variables it declares, all of them or by direction`() {
        val variables = FlowNodes.CallActivityCancelBikeOrder.variables

        assertThat(variables.all).containsExactly(variables.APPLICATION_ID, variables.CANCELLATION_COSTS, variables.ORDER_IDS)
        assertThat(variables.inputs).containsExactly(variables.APPLICATION_ID, variables.ORDER_IDS)
        assertThat(variables.outputs).containsExactly(variables.CANCELLATION_COSTS)
    }

    @Test
    fun `the variables facet reaches the variables of any node generically`() {
        val nodesWithVariables = FlowNodes.all.filterIsInstance<HasVariables>()

        assertThat(nodesWithVariables).contains(FlowNodes.CallActivityCancelBikeOrder, FlowNodes.ServiceTaskOrderBike)
        assertThat(nodesWithVariables).allSatisfy { node -> assertThat(node.variables.all).isNotEmpty() }
        assertThat(HasVariables.distinctVariablesOf(listOf(FlowNodes.ServiceTaskOrderBike))).containsExactly("bikeId", "bikeIds")
    }
}
