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

class TimerCronSyntaxRuleTest {

    private val underTest = TimerCronSyntaxRule()

    @Test
    fun `has the expected id and severity`() {
        assertThat(underTest.id).isEqualTo("timer-cron-syntax")
        assertThat(underTest.severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `no violation for a valid cron cycle`() {
        assertThat(validate("Timer_1", TimerType.CYCLE, "0 0 9 ? * MON-FRI")).isEmpty()
    }

    @Test
    fun `reports an error for an invalid cron cycle`() {
        val violations = validate("Timer_Bad", TimerType.CYCLE, "not a cron")
        assertThat(violations).hasSize(1)
        assertThat(violations.single().elementId).isEqualTo("Timer_Bad")
        assertThat(violations.single().severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `reports an error for a cron cycle with the wrong field count`() {
        assertThat(validate("Timer_Bad", TimerType.CYCLE, "0 0 9 * *")).hasSize(1)
    }

    @Test
    fun `ignores non-cycle timers`() {
        assertThat(validate("Timer_D", TimerType.DURATION, "PT15M")).isEmpty()
        assertThat(validate("Timer_Dt", TimerType.DATE, "2026-01-01T00:00:00Z")).isEmpty()
    }

    @Test
    fun `skips expression and blank values`() {
        assertThat(validate("Timer_Feel", TimerType.CYCLE, "=cronVar")).isEmpty()
        assertThat(validate("Timer_El", TimerType.CYCLE, "\${cronVar}")).isEmpty()
        assertThat(validate("Timer_Blank", TimerType.CYCLE, "")).isEmpty()
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
