package io.miragon.bpmn.domain.validation.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TimerValueSyntaxTest {

    @Test
    fun `detects dynamic expressions`() {
        assertThat(TimerValueSyntax.isExpression("=cronVar")).isTrue()
        assertThat(TimerValueSyntax.isExpression("\${var}")).isTrue()
        assertThat(TimerValueSyntax.isExpression("#{var}")).isTrue()
        assertThat(TimerValueSyntax.isExpression("PT15M")).isFalse()
    }

    @Test
    fun `accepts valid cron and rejects invalid cron`() {
        assertThat(TimerValueSyntax.isValidCron("0 0 9 ? * MON-FRI")).isTrue()
        assertThat(TimerValueSyntax.isValidCron("0 0 9 * * ? 2026")).isTrue()
        assertThat(TimerValueSyntax.isValidCron("not a cron")).isFalse()
        assertThat(TimerValueSyntax.isValidCron("0 0 9 * *")).isFalse()
        assertThat(TimerValueSyntax.isValidCron("")).isFalse()
    }

    @Test
    fun `accepts valid iso durations and rejects invalid ones`() {
        assertThat(TimerValueSyntax.isValidIsoDuration("PT15M")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoDuration("P1Y2M")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoDuration("P1DT12H")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoDuration("15 minutes")).isFalse()
        assertThat(TimerValueSyntax.isValidIsoDuration("R3/PT10M")).isFalse()
    }

    @Test
    fun `accepts valid iso date-times and rejects invalid ones`() {
        assertThat(TimerValueSyntax.isValidIsoDateTime("2026-01-01T00:00:00Z")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoDateTime("2026-01-01T00:00:00")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoDateTime("01/01/2026")).isFalse()
        assertThat(TimerValueSyntax.isValidIsoDateTime("not-a-date")).isFalse()
    }

    @Test
    fun `accepts valid iso repeating intervals and rejects invalid ones`() {
        assertThat(TimerValueSyntax.isValidIsoRepeatingInterval("R3/PT10M")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoRepeatingInterval("R/PT1H")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoRepeatingInterval("R5/2026-01-01T00:00:00Z/PT1H")).isTrue()
        assertThat(TimerValueSyntax.isValidIsoRepeatingInterval("PT10M")).isFalse()
        assertThat(TimerValueSyntax.isValidIsoRepeatingInterval("0 0 9 * * ?")).isFalse()
    }
}
