package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TimerSyntaxRuleTest {

    @Test
    fun `cron rule flags only the invalid cron cycle`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/timer-syntax.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.TIMER_CRON_SYNTAX)
            .validate()
            .result()

        assertThat(result.violations).hasSize(1)
        assertThat(result.violations.single().elementId).isEqualTo("Timer_BadCron")
    }

    @Test
    fun `iso rule validates by type and skips expressions`() {
        val result = BpmnValidator
            .fromClasspath("bpmn/timer-syntax.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(BpmnRules.TIMER_ISO8601_SYNTAX)
            .validate()
            .result()

        val flagged = result.violations.map { it.elementId }
        // Both cron cycles are not valid ISO repeating intervals:
        assertThat(flagged).containsExactlyInAnyOrder("Timer_BadCron", "Timer_GoodCron")
        // Valid ISO duration/date are accepted; the dynamic expression is skipped:
        assertThat(flagged).doesNotContain("Timer_Duration", "Timer_Date", "Timer_FeelExpr")
    }
}
