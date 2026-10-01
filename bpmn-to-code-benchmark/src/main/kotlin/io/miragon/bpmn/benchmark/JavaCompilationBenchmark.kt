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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

/**
 * What the generated Java costs the projects using it:
 * how long javac takes to compile it and how many class files it produces.
 * Compiles in memory against the runtime library on the classpath.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
open class JavaCompilationBenchmark {

    @Param(Scenario.WEB_SAMPLE)
    @JvmField
    var scenario = ""

    private lateinit var sources: List<GeneratedSource>

    @Setup
    fun generateSources() {
        sources = Generator().generate(Scenario.named(scenario), language = "JAVA")
    }

    @Benchmark
    fun compile(output: CompiledOutput) {
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Compiling needs a JDK, not a JRE" }
        val fileManager = InMemoryClassFiles(compiler.getStandardFileManager(null, null, null))
        val units = sources.map { SourceFile(it) }
        val options = listOf("-proc:none", "-classpath", System.getProperty("java.class.path"))
        val compiled = compiler.getTask(null, fileManager, null, options, null, units).call()
        check(compiled) { "The generated Java does not compile" }
        output.classFiles = fileManager.classFileCount
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    open class CompiledOutput {

        @JvmField
        var classFiles = 0
    }

    private class SourceFile(
        private val source: GeneratedSource,
    ) : SimpleJavaFileObject(sourceUri(source), JavaFileObject.Kind.SOURCE) {

        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source.content
    }

    private class ClassFile(
        className: String,
        kind: JavaFileObject.Kind,
    ) : SimpleJavaFileObject(classFileUri(className, kind), kind) {

        override fun openOutputStream(): OutputStream = ByteArrayOutputStream()
    }

    private class InMemoryClassFiles(
        standard: StandardJavaFileManager,
    ) : ForwardingJavaFileManager<StandardJavaFileManager>(standard) {

        var classFileCount = 0
            private set

        override fun getJavaFileForOutput(
            location: JavaFileManager.Location,
            className: String,
            kind: JavaFileObject.Kind,
            sibling: FileObject?,
        ): JavaFileObject {
            classFileCount++
            return ClassFile(className, kind)
        }
    }

    companion object {
        val SCENARIOS = setOf("bike-leasing", "synthetic-1000")

        private fun sourceUri(source: GeneratedSource): URI {
            val directory = source.packagePath.replace('.', '/')
            return URI.create("string:///$directory/${source.fileName}")
        }

        private fun classFileUri(className: String, kind: JavaFileObject.Kind): URI {
            val path = className.replace('.', '/')
            return URI.create("memory:///$path${kind.extension}")
        }
    }
}
