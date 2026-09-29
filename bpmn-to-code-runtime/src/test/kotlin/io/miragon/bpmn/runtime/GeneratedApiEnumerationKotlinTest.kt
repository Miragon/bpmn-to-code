package io.miragon.bpmn.runtime

import io.miragon.bpmn.runtime.path.example.BikeLeasingProcessApi.FlowNodes
import io.miragon.bpmn.runtime.path.example.Errors
import io.miragon.bpmn.runtime.path.example.Escalations
import io.miragon.bpmn.runtime.path.example.Messages
import io.miragon.bpmn.runtime.path.example.ServiceTasks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Enumerates the generated bike-leasing API from **Kotlin**: `FlowNodes.entries` and the shared definitions'
 * `entries`. The `GeneratedApiEnumerationJavaTest` sibling covers the Java `all()` accessors.
 */
class GeneratedApiEnumerationKotlinTest {

    @Test
    fun `flow entries list exactly the nodes of the process`() {
        val everyNode = FlowNodes::class.nestedClasses.map { it.objectInstance }.filterIsInstance<FlowNode>()

        assertThat(FlowNodes.entries).hasSize(42).containsExactlyInAnyOrderElementsOf(everyNode)
    }

    @Test
    fun `shared definitions list exactly their values in declaration order`() {
        assertThat(ServiceTasks.entries).containsExactly(
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
        assertThat(Messages.entries).containsExactly(
            Messages.MIRAVELO_ADDRESS_CHANGED,
            Messages.MIRAVELO_APPLICATION_WITHDRAWN,
            Messages.MIRAVELO_CONTRACT_SIGNED,
            Messages.MIRAVELO_HANDOVER_REPORTED,
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED,
        )
        assertThat(Errors.entries).containsExactly(Errors.MIRAVELO_APPLICATION_INVALID)
        assertThat(Escalations.entries).containsExactly(Escalations.MIRAVELO_CONTRACT_NOT_SIGNED)
    }
}
