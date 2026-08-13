package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingCalledElementRuleTest {

    private val underTest = MissingCalledElementRule()

    @Test
    fun `reports error for call activity with null calledElement`() {

        // given: a call activity with no calledElement set
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.CallActivity(
                    id = "call1",
                    definition = CallActivityDefinition(id = "call1", calledElement = null),
                ),
            ),
        )

        // when / then: an ERROR violation is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("call1")
    }

    @Test
    fun `no violations for call activity with calledElement`() {

        // given: a call activity with a valid calledElement reference
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Activity.CallActivity(
                    id = "call1",
                    definition = CallActivityDefinition(id = "call1", calledElement = "my-sub-process"),
                ),
            ),
        )

        // when / then: no violations
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }
}
