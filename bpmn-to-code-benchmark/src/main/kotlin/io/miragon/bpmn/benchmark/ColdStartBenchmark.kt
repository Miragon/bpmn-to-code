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
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import com.sun.management.OperatingSystemMXBean as CpuAwareOperatingSystemMXBean

/**
 * The first generations in a fresh JVM: what a web request costs right after the app (re)started.
 * Every fork is sized like the web container: one CPU and the Dockerfile's heap settings.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 10, jvmArgsAppend = ["-XX:ActiveProcessorCount=1", "-Xms256m", "-Xmx512m"])
@Warmup(iterations = 0)
@Measurement(iterations = 1)
open class ColdStartBenchmark {

    @Param("KOTLIN", "JAVA")
    @JvmField
    var language = ""

    val scenario = Scenario.webSample()

    @Benchmark
    fun firstCall(cost: FirstCallCost): List<GeneratedSource> {
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean() as CpuAwareOperatingSystemMXBean
        val classLoading = ManagementFactory.getClassLoadingMXBean()
        val cpuBefore = operatingSystem.processCpuTime
        val classesBefore = classLoading.loadedClassCount
        val sources = Generator().generate(scenario, language)
        cost.cpuMillis = (operatingSystem.processCpuTime - cpuBefore) / NANOS_PER_MILLI
        cost.classesLoaded = classLoading.loadedClassCount - classesBefore
        return sources
    }

    @Benchmark
    fun secondCall(calledOnce: CalledOnce): List<GeneratedSource> = calledOnce.generator.generate(scenario, language)

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    open class FirstCallCost {

        @JvmField
        var cpuMillis = 0.0

        @JvmField
        var classesLoaded = 0
    }

    @State(Scope.Benchmark)
    open class CalledOnce {

        lateinit var generator: Generator

        @Setup
        fun callOnce(benchmark: ColdStartBenchmark) {
            generator = Generator()
            generator.generate(benchmark.scenario, benchmark.language)
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
