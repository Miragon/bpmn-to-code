package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.testBpmnModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationContext
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
        assertThat(validate("Timer_1", "Cycle", "0 0 9 ? * MON-FRI")).isEmpty()
    }

    @Test
    fun `reports an error for an invalid cron cycle`() {
        val violations = validate("Timer_Bad", "Cycle", "not a cron")
        assertThat(violations).hasSize(1)
        assertThat(violations.single().elementId).isEqualTo("Timer_Bad")
        assertThat(violations.single().severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `reports an error for a cron cycle with the wrong field count`() {
        assertThat(validate("Timer_Bad", "Cycle", "0 0 9 * *")).hasSize(1)
    }

    @Test
    fun `ignores non-cycle timers`() {
        assertThat(validate("Timer_D", "Duration", "PT15M")).isEmpty()
        assertThat(validate("Timer_Dt", "Date", "2026-01-01T00:00:00Z")).isEmpty()
    }

    @Test
    fun `skips expression and blank values`() {
        assertThat(validate("Timer_Feel", "Cycle", "=cronVar")).isEmpty()
        assertThat(validate("Timer_El", "Cycle", "\${cronVar}")).isEmpty()
        assertThat(validate("Timer_Blank", "Cycle", "")).isEmpty()
    }

    private fun validate(id: String, type: String?, value: String?): List<ValidationViolation> {
        val model = testBpmnModel(
            flowNodes = listOf(
                FlowNodeDefinition(
                    id = id,
                    properties = FlowNodeProperties.Timer(TimerDefinition(id = id, type = type, value = value)),
                ),
            ),
        )
        return underTest.validate(ValidationContext(model = model, engine = ProcessEngine.ZEEBE))
    }
}
