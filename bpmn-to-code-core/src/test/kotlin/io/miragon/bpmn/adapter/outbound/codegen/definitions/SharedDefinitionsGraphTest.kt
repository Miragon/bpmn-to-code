package io.miragon.bpmn.adapter.outbound.codegen.definitions

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedDefinitionsGraphTest {

    @Test
    fun `groups the definitions by kind in the order of the shared definition types`() {
        // given: an escalation, a message and a service task
        val definitions = SharedDefinitions(
            serviceTasks = listOf(ServiceTaskDefinition(id = "serviceTask_checkBike", implementation = TaskImplementation.JobWorker("checkBike"))),
            messages = listOf(RootElementDefinition.Message(id = "message_bikeReturned", name = "bikeReturned")),
            escalations = listOf(RootElementDefinition.Escalation(id = "escalation_late", name = "returnLate", code = "LATE")),
        )

        // when: building the graph
        val graph = SharedDefinitionsGraph.of(definitions)

        // then: only kinds with definitions form a group, ordered like the enum
        assertThat(graph.groups.map { it.type }).containsExactly(
            SharedDefinitionType.SERVICE_TASKS,
            SharedDefinitionType.MESSAGES,
            SharedDefinitionType.ESCALATIONS,
        )
    }

    @Test
    fun `keeps the code only for the kinds that carry one`() {
        // given: a signal and an error
        val definitions = SharedDefinitions(
            signals = listOf(RootElementDefinition.Signal(id = "signal_memberActivated", name = "memberActivated")),
            errors = listOf(RootElementDefinition.Error(id = "error_damaged", name = "bikeDamaged", code = "DAMAGED")),
        )

        // when: building the graph
        val graph = SharedDefinitionsGraph.of(definitions)

        // then: the signal is a plain value, the error carries name and code
        assertThat(graph.groups.flatMap { it.entries }).containsExactly(
            SharedDefinitionsGraph.Entry(constantName = "MEMBER_ACTIVATED", value = "memberActivated", code = null),
            SharedDefinitionsGraph.Entry(constantName = "BIKE_DAMAGED", value = "bikeDamaged", code = "DAMAGED"),
        )
    }

    @Test
    fun `has no groups without definitions`() {
        val graph = SharedDefinitionsGraph.of(SharedDefinitions())

        assertThat(graph.groups).isEmpty()
    }
}
