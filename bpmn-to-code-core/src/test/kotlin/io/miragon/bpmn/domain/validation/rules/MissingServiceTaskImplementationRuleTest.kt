package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingServiceTaskImplementationRuleTest {

    private val underTest = MissingServiceTaskImplementationRule()

    /**
     * A service task that is declared service-task-like but carries no implementation configuration.
     */
    private fun unimplementedServiceTask(id: String) = FlowNodeDefinition.Activity.Task(
        id = id,
        kind = TaskKind.SERVICE,
        implementation = TaskImplementation.Unspecified,
    )

    @Test
    fun `reports error for service task with no implementation`() {
        // given: a service task with no implementation
        val model = testProcessModel(flowNodes = listOf(unimplementedServiceTask("task1")))

        // when: validating against Zeebe
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))

        // then: an ERROR violation mentioning zeebe:taskDefinition
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("task1")
        assertThat(violations[0].message).contains("zeebe:taskDefinition")
    }

    @Test
    fun `reports every unimplemented service task, not just the first`() {
        // given: three service tasks that all lack an implementation
        val model = testProcessModel(
            flowNodes = listOf(
                unimplementedServiceTask("task1"),
                unimplementedServiceTask("task2"),
                unimplementedServiceTask("task3"),
            ),
        )

        // when
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))

        // then: each one is named — they are distinct elements even though they share an empty implementation
        assertThat(violations.map { it.elementId }).containsExactlyInAnyOrder("task1", "task2", "task3")
    }

    @Test
    fun `no violations for service task with valid implementation`() {
        // given: a service task with a resolved job-worker implementation
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.Task(
                    id = "task1",
                    kind = TaskKind.SERVICE,
                    implementation = TaskImplementation.JobWorker("myWorker"),
                ),
            ),
        )

        // when / then: no violations (for any engine)
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.CAMUNDA_7))
        assertThat(violations).isEmpty()
    }

    @Test
    fun `engine-specific hint for Camunda 7`() {
        // given: a service task with no implementation validated against Camunda 7
        val model = testProcessModel(flowNodes = listOf(unimplementedServiceTask("task1")))

        // when / then: the violation message mentions camunda:topic
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.CAMUNDA_7))
        assertThat(violations[0].message).contains("camunda:topic")
    }

    @Test
    fun `engine-specific hint for Operaton`() {
        // given: a service task with no implementation validated against Operaton
        val model = testProcessModel(flowNodes = listOf(unimplementedServiceTask("task1")))

        // when / then: the violation message mentions operaton:topic
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.OPERATON))
        assertThat(violations[0].message).contains("operaton:topic")
    }
}
