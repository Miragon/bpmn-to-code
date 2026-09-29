package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TimerIso8601SyntaxRuleTest {

    private val underTest = TimerIso8601SyntaxRule()

    @Test
    fun `has the expected id and severity`() {
        assertThat(underTest.id).isEqualTo("timer-iso8601-syntax")
        assertThat(underTest.severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `no violation for valid iso values per type`() {
        assertThat(validate(id = "Timer_Date", type = TimerType.DATE, value = "2026-01-01T00:00:00Z")).isEmpty()
        assertThat(validate(id = "Timer_Dur", type = TimerType.DURATION, value = "PT15M")).isEmpty()
        assertThat(validate(id = "Timer_Dur2", type = TimerType.DURATION, value = "P1Y2M")).isEmpty()
        assertThat(validate(id = "Timer_Cyc", type = TimerType.CYCLE, value = "R3/PT10M")).isEmpty()
    }

    @Test
    fun `reports an error for an invalid iso duration`() {
        val violations = validate(id = "Timer_Bad", type = TimerType.DURATION, value = "15 minutes")
        assertThat(violations).hasSize(1)
        assertThat(violations.single().elementId).isEqualTo("Timer_Bad")
        assertThat(violations.single().severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `reports an error for an invalid iso date`() {
        assertThat(validate(id = "Timer_Bad", type = TimerType.DATE, value = "01/01/2026")).hasSize(1)
    }

    @Test
    fun `reports an error for a cron cycle under the iso rule`() {
        assertThat(validate(id = "Timer_Bad", type = TimerType.CYCLE, value = "0 0 9 * * ?")).hasSize(1)
    }

    @Test
    fun `skips expression and blank values`() {
        assertThat(validate(id = "Timer_Feel", type = TimerType.DURATION, value = "=durationVar")).isEmpty()
        assertThat(validate(id = "Timer_El", type = TimerType.DURATION, value = "\${durationVar}")).isEmpty()
        assertThat(validate(id = "Timer_Blank", type = TimerType.DURATION, value = "")).isEmpty()
    }

    @Test
    fun `ignores timers with an unknown type`() {
        assertThat(validate(id = "Timer_NoType", type = null, value = "whatever")).isEmpty()
    }

    private fun validate(id: String, type: TimerType?, value: String?): List<ValidationViolation> {
        val model = testProcessModel(
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = id,
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(type, value)),
                ),
            ),
        )
        return underTest.validate(SingleModelValidationContext(model = model, engine = ProcessEngine.ZEEBE))
    }
}
