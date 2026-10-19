package io.miragon.bpmn.adapter.outbound.codegen.surface

import io.miragon.bpmn.domain.GeneratedApiFile
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * Compiles generated Kotlin or Java against the runtime library on this JVM's classpath and loads the result.
 */
internal object GeneratedSourceCompiler {

    fun compileKotlin(files: List<GeneratedApiFile>): ClassLoader {
        val workDir = Files.createTempDirectory("kotlin-compile").toFile()
        val sources = writeSources(workDir, files)
        val outDir = File(workDir, "out")
        val output = ByteArrayOutputStream()
        val arguments = arrayOf(
            "-no-stdlib",
            "-no-reflect",
            "-jvm-target", "21",
            "-classpath", System.getProperty("java.class.path"),
            "-d", outDir.absolutePath,
            sources.absolutePath,
        )
        val exitCode = K2JVMCompiler().exec(PrintStream(output), *arguments)
        check(exitCode == ExitCode.OK) { "Generated Kotlin did not compile:\n$output" }
        return classLoaderFor(outDir)
    }

    fun compileJava(files: List<GeneratedApiFile>): ClassLoader {
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "JDK (not JRE) required to compile Java" }
        val workDir = Files.createTempDirectory("java-compile").toFile()
        val sources = writeSources(workDir, files)
        val outDir = File(workDir, "out").apply { mkdirs() }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        compiler.getStandardFileManager(diagnostics, null, null).use { fileManager ->
            val units = fileManager.getJavaFileObjectsFromFiles(sources.walk().filter { it.isFile }.toList())
            val options = listOf("-classpath", System.getProperty("java.class.path"), "-d", outDir.absolutePath)
            compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
        }
        val errors = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
        check(errors.isEmpty()) {
            val messages = errors.map { "${it.source?.name}:${it.lineNumber}: ${it.getMessage(null)}" }
            "Generated Java did not compile:\n${messages.joinToString("\n")}"
        }
        return classLoaderFor(outDir)
    }

    private fun writeSources(workDir: File, files: List<GeneratedApiFile>): File {
        val sourceDir = File(workDir, "src")
        files.forEach { file ->
            File(sourceDir, file.fileName).apply { parentFile.mkdirs() }.writeText(file.content)
        }
        return sourceDir
    }

    private fun classLoaderFor(outDir: File): ClassLoader = URLClassLoader(arrayOf(outDir.toURI().toURL()), GeneratedSourceCompiler::class.java.classLoader)
}
