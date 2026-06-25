package io.miragon.bpmn.domain.validation.rules

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.xml.datatype.DatatypeFactory

/**
 * Syntactic checks for BPMN timer values, shared by [TimerCronSyntaxRule] and
 * [TimerIso8601SyntaxRule].
 * All checks operate on the raw timer value string (the verbatim `timeDate` /
 * `timeDuration` / `timeCycle` text). Dynamic expressions (Zeebe FEEL `=...`,
 * Camunda EL) cannot be validated statically — callers detect them via
 * [isExpression] and skip them.
 */
internal object TimerValueSyntax {

    private val datatypeFactory = DatatypeFactory.newInstance()

    private val cronField = Regex("^[0-9A-Za-z*?/,\\-#]+$")

    private val repeatPrefix = Regex("^R\\d*$")

    /**
     * A dynamic expression (FEEL or Camunda EL) whose value is only
     * known at runtime.
     */
    fun isExpression(value: String): Boolean {
        return value.startsWith("=") || value.contains("\${") || value.contains("#{")
    }

    /**
     * Structural cron check: 6 or 7 whitespace-separated fields
     * (Quartz / Spring dialects).
     */
    fun isValidCron(value: String): Boolean {
        val fields = value.trim().split(Regex("\\s+"))
        if (fields.size !in 6..7) return false
        return fields.all { it.isNotEmpty() && cronField.matches(it) }
    }

    /**
     * ISO-8601 duration, e.g. `PT15M`, `P1Y2M`, `P1DT12H`
     * (full xsd:duration grammar).
     */
    fun isValidIsoDuration(value: String): Boolean {
        return value.startsWith("P") && runCatching { datatypeFactory.newDuration(value) }.isSuccess
    }

    /**
     * ISO-8601 point in time, e.g. `2026-01-01T00:00:00Z` or
     * `2026-01-01T00:00:00`.
     */
    fun isValidIsoDateTime(value: String): Boolean {
        val parsers = listOf<(String) -> Any>(
            { OffsetDateTime.parse(it) },
            { ZonedDateTime.parse(it) },
            { LocalDateTime.parse(it) },
            { Instant.parse(it) },
        )
        return parsers.any { parse -> runCatching { parse(value) }.isSuccess }
    }

    /**
     * ISO-8601 repeating interval, e.g. `R3/PT10M` or
     * `R/2026-01-01T00:00:00Z/PT1H`.
     */
    fun isValidIsoRepeatingInterval(value: String): Boolean {
        val parts = value.split("/")
        if (parts.size < 2 || !repeatPrefix.matches(parts[0])) return false
        return parts.drop(1).all { isValidIsoDuration(it) || isValidIsoDateTime(it) }
    }
}
