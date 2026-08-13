package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingTimerDefinitionRuleTest {

    private val underTest = MissingTimerDefinitionRule()

    @Test
    fun `reports error for timer with no type`() {

        // given: a timer event carrying a definition with neither a type nor an expression
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "timer1",
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(timerType = null, expression = null)),
                ),
            ),
        )

        // when / then: an ERROR violation is reported
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("timer1")
    }

    @Test
    fun `no violations for timer with type and expression`() {

        // given: a timer event with a valid type and expression
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "timer1",
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1H")),
                ),
            ),
        )

        // when / then: no violations
        val violations = underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
        assertThat(violations).isEmpty()
    }
}
