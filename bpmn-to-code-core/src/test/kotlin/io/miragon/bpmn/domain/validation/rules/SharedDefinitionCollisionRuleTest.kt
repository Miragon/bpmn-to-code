package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedDefinitionCollisionRuleTest {

    private val underTest = SharedDefinitionCollisionRule()

    @Test
    fun `is mandatory`() {
        assertThat(underTest.mandatory).isTrue()
    }

    @Test
    fun `reports messages of two processes that normalize to the same constant`() {
        // given: two processes whose message names differ only in separator
        val first = testProcessModel(processId = "first", messages = listOf(RootElementDefinition.Message(id = "m1", name = "order.created")))
        val second = testProcessModel(processId = "second", messages = listOf(RootElementDefinition.Message(id = "m2", name = "order-created")))

        // when: validating across both models
        val violations = underTest.validate(CrossModelValidationContext(listOf(first, second), ProcessEngine.ZEEBE))

        // then: one error names both processes and both names
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].processId).isEqualTo("first, second")
        assertThat(violations[0].message).isEqualTo("[Message] 'ORDER_CREATED' has conflicting IDs: order-created, order.created")
    }

    @Test
    fun `no violations when processes share the same identifier`() {
        // given: two processes using the same message
        val first = testProcessModel(processId = "first", messages = listOf(RootElementDefinition.Message(id = "m1", name = "order.created")))
        val second = testProcessModel(processId = "second", messages = listOf(RootElementDefinition.Message(id = "m2", name = "order.created")))

        // when / then: nothing is reported
        assertThat(underTest.validate(CrossModelValidationContext(listOf(first, second), ProcessEngine.ZEEBE))).isEmpty()
    }
}
