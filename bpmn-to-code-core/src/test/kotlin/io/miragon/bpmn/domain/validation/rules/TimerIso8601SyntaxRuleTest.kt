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

class TimerIso8601SyntaxRuleTest {

    private val underTest = TimerIso8601SyntaxRule()

    @Test
    fun `has the expected id and severity`() {
        assertThat(underTest.id).isEqualTo("timer-iso8601-syntax")
        assertThat(underTest.severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `no violation for valid iso values per type`() {
        assertThat(validate("Timer_Date", "Date", "2026-01-01T00:00:00Z")).isEmpty()
        assertThat(validate("Timer_Dur", "Duration", "PT15M")).isEmpty()
        assertThat(validate("Timer_Dur2", "Duration", "P1Y2M")).isEmpty()
        assertThat(validate("Timer_Cyc", "Cycle", "R3/PT10M")).isEmpty()
    }

    @Test
    fun `reports an error for an invalid iso duration`() {
        val violations = validate("Timer_Bad", "Duration", "15 minutes")
        assertThat(violations).hasSize(1)
        assertThat(violations.single().elementId).isEqualTo("Timer_Bad")
        assertThat(violations.single().severity).isEqualTo(Severity.ERROR)
    }

    @Test
    fun `reports an error for an invalid iso date`() {
        assertThat(validate("Timer_Bad", "Date", "01/01/2026")).hasSize(1)
    }

    @Test
    fun `reports an error for a cron cycle under the iso rule`() {
        assertThat(validate("Timer_Bad", "Cycle", "0 0 9 * * ?")).hasSize(1)
    }

    @Test
    fun `skips expression and blank values`() {
        assertThat(validate("Timer_Feel", "Duration", "=durationVar")).isEmpty()
        assertThat(validate("Timer_El", "Duration", "\${durationVar}")).isEmpty()
        assertThat(validate("Timer_Blank", "Duration", "")).isEmpty()
    }

    @Test
    fun `ignores timers with an unknown type`() {
        assertThat(validate("Timer_NoType", null, "whatever")).isEmpty()
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
