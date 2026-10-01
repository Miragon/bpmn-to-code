package io.miragon.bpmn.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Prints the JMH results of two generators side by side as Markdown tables, ready to paste into a pull request.
 * Every cell reads `baseline → current (ratio)`, with JMH's 99.9 % error margin behind each measured time.
 */
object ResultComparison {

    private const val PRIMARY_METRIC = "ms"
    private const val BYTES_PER_MEGABYTE = 1_000_000.0

    private val secondaryMetrics = listOf(
        SecondaryMetric(name = "gc.alloc.rate.norm", label = "MB allocated", divisor = BYTES_PER_MEGABYTE),
        SecondaryMetric(name = "cpuMillis", label = "CPU ms"),
        SecondaryMetric(name = "classesLoaded", label = "classes loaded"),
        SecondaryMetric(name = "files", label = "files"),
        SecondaryMetric(name = "lines", label = "lines"),
        SecondaryMetric(name = "classFiles", label = "class files"),
    )

    fun print(baselineLabel: String, baseline: Path, current: Path) {
        val baselineResults = read(baseline).associateBy { it.benchmark to it.parameters }
        val currentResults = read(current).filter { (it.benchmark to it.parameters) in baselineResults }
        if (currentResults.isEmpty()) {
            println("\nNo results to compare.")
            return
        }
        currentResults.groupBy { it.benchmark }.forEach { (benchmark, results) ->
            val parameterNames = results.first().parameters.keys.toList()
            val metricNames = results.first().measurements.keys.toList()
            println("\n### $benchmark: $baselineLabel → current (ratio = current / $baselineLabel)\n")
            println(markdownRow(parameterNames + metricNames))
            println(markdownRow(List(parameterNames.size + metricNames.size) { "---" }))
            results.forEach { result ->
                val baselineResult = baselineResults.getValue(result.benchmark to result.parameters)
                val cells = metricNames.map { comparison(baselineResult.measurements[it], result.measurements[it]) }
                println(markdownRow(result.parameters.values + cells))
            }
        }
    }

    private fun read(file: Path): List<BenchmarkResult> {
        if (!Files.exists(file)) {
            return emptyList()
        }
        val results = Json.parseToJsonElement(Files.readString(file)).jsonArray
        return results.map { toResult(it.jsonObject) }
    }

    private fun toResult(result: JsonObject): BenchmarkResult {
        val parameters = result["params"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
        val measurements = linkedMapOf(PRIMARY_METRIC to measuredTime(result.getValue("primaryMetric").jsonObject))
        val secondary = result.getValue("secondaryMetrics").jsonObject
        secondaryMetrics.forEach { metric ->
            val measured = secondary[metric.name]?.jsonObject
            if (measured != null) {
                val score = averagePerIteration(measured) / metric.divisor
                measurements[metric.label] = Measurement(score = score, error = Double.NaN)
            }
        }
        return BenchmarkResult(
            benchmark = result.getValue("benchmark").jsonPrimitive.content.substringAfterLast('.'),
            parameters = parameters,
            measurements = measurements,
        )
    }

    private fun measuredTime(metric: JsonObject): Measurement = Measurement(
        score = metric.getValue("score").jsonPrimitive.content.toDouble(),
        error = metric.getValue("scoreError").jsonPrimitive.content.toDouble(),
    )

    /**
     * JMH sums counters such as the generated lines over all iterations instead of averaging them,
     * so the value of a secondary metric is taken from its raw data.
     */
    private fun averagePerIteration(metric: JsonObject): Double {
        val forks = metric.getValue("rawData").jsonArray
        val iterations = forks.flatMap { fork -> fork.jsonArray.map { it.jsonPrimitive.double } }
        return iterations.average()
    }

    private fun comparison(baseline: Measurement?, current: Measurement?): String {
        if (baseline == null || current == null) {
            return "–"
        }
        if (baseline.score == 0.0) {
            return "${format(baseline)} → ${format(current)}"
        }
        val ratio = String.format(Locale.ROOT, "%.2f", current.score / baseline.score)
        return "${format(baseline)} → ${format(current)} ($ratio×)"
    }

    private fun format(measurement: Measurement): String {
        val score = format(measurement.score)
        if (measurement.error.isNaN()) {
            return score
        }
        return "$score ±${format(measurement.error)}"
    }

    private fun format(value: Double): String {
        val pattern = when {
            value % 1.0 == 0.0 || value >= 100 -> "%.0f"
            value >= 10 -> "%.1f"
            else -> "%.2f"
        }
        return String.format(Locale.ROOT, pattern, value)
    }

    private fun markdownRow(cells: List<String>): String {
        val row = cells.joinToString(separator = " | ")
        return "| $row |"
    }

    private data class SecondaryMetric(val name: String, val label: String, val divisor: Double = 1.0)

    private data class Measurement(val score: Double, val error: Double)

    private data class BenchmarkResult(
        val benchmark: String,
        val parameters: Map<String, String>,
        val measurements: Map<String, Measurement>,
    )
}
