package io.miragon.bpmn.benchmark

import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler
import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.nio.file.Path

private val GENERATION = GenerationBenchmark::class.java.simpleName

fun main(args: Array<String>) {
    val mode = args[0]
    if (mode == "compare") {
        ResultComparison.print(baselineLabel = args[1], baseline = Path.of(args[2]), current = Path.of(args[3]))
        return
    }
    val resultFile = Path.of(args[1])
    val scenarios = Scenario.all().filter { it.name.contains(args[2]) }
    val options = OptionsBuilder().resultFormat(ResultFormatType.JSON).result(resultFile.toString())
    when (mode) {
        "warm" -> {
            options.include(GENERATION).scenarios(scenarios).addProfiler(GCProfiler::class.java)
        }

        "interpreted" -> {
            options.include(GENERATION).scenarios(scenarios.filterNot { it.heavy }).addProfiler(GCProfiler::class.java)
            options.jvmArgsAppend("-Xint").forks(1).warmupIterations(1)
        }

        "cold" -> {
            options.include(ColdStartBenchmark::class.java.simpleName)
        }

        "compile" -> {
            val compiled = scenarios.filter { it.name in JavaCompilationBenchmark.SCENARIOS }
            options.include(JavaCompilationBenchmark::class.java.simpleName).scenarios(compiled)
        }

        "profile" -> {
            val recordings = resultFile.resolveSibling("recordings")
            options.include("$GENERATION.generate").scenarios(scenarios.take(1)).forks(1)
            options.addProfiler(JavaFlightRecorderProfiler::class.java, "dir=$recordings;configName=profile")
        }

        else -> {
            error("Unknown benchmark mode '$mode'")
        }
    }
    Runner(options.build()).run()
}

private fun ChainedOptionsBuilder.scenarios(scenarios: List<Scenario>): ChainedOptionsBuilder {
    check(scenarios.isNotEmpty()) { "No scenario matches" }
    scenarios.forEach { param("scenario", it.name) }
    return this
}
