package io.miragon.bpmn.benchmark

import io.miragon.bpmn.benchmark.Generator.GeneratedSource
import org.openjdk.jmh.annotations.AuxCounters
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * How long one generation takes in a warmed-up JVM,
 * how much of it is spent reading the BPMN files,
 * and how much code it produces.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
open class GenerationBenchmark {

    @Param(Scenario.WEB_SAMPLE)
    @JvmField
    var scenario = ""

    private lateinit var generator: Generator
    private lateinit var input: Scenario

    @Setup
    fun prepare() {
        generator = Generator()
        input = Scenario.named(scenario)
    }

    @Benchmark
    fun generate(output: Output, size: GeneratedSize): List<GeneratedSource> {
        size.files = output.fileCount
        size.lines = output.lineCount
        return generator.generate(input, output.language)
    }

    @Benchmark
    fun parse() {
        generator.parse(input)
    }

    @State(Scope.Benchmark)
    open class Output {

        @Param("KOTLIN", "JAVA")
        @JvmField
        var language = ""

        var fileCount = 0
        var lineCount = 0

        @Setup
        fun measureSize(benchmark: GenerationBenchmark) {
            val sources = benchmark.generator.generate(benchmark.input, language)
            fileCount = sources.size
            lineCount = sources.sumOf { it.lineCount }
        }
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    open class GeneratedSize {

        @JvmField
        var files = 0

        @JvmField
        var lines = 0
    }
}
