package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * Regression gate for a subprocess nested inside another subprocess: every nesting level repeats the `Next` and
 * `Start` holder names, and Java forbids a nested type sharing a simple name with an enclosing one (JLS 8.1.3).
 * The existing golden tests only *parse* the output, so they can't catch this; here we generate the Java API and
 * actually **compile** it against the runtime interfaces.
 */
class NestedSubProcessCompilationTest {

    private val service = GenerateProcessApiInMemoryService()

    @Test
    fun `generated java for nested subprocesses compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/nested-subprocess.bpmn")).readText()
        assertCompiles(generate(listOf(bpmnXml)))
    }

    @Test
    fun `generated java of two processes sharing job types and messages compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn")).readText()
        val copy = bpmnXml.replace("id=\"newsletterSubscription\"", "id=\"newsletterSubscriptionCopy\"")
        val generated = generate(listOf(bpmnXml, copy))
        assertThat(generated.map { it.fileName }).contains("ServiceTasks.java", "Messages.java")
        assertThat(generated.filter { it.fileName == "ServiceTasks.java" }).hasSize(1)
        assertCompiles(generated)
    }

    private fun assertCompiles(generated: List<GeneratedApiFile>) {
        val errors = compileJava(generated)
        assertThat(errors)
            .withFailMessage { "Generated Java did not compile:\n${errors.joinToString("\n")}" }
            .isEmpty()
    }

    private fun generate(bpmnXmls: List<String>) = service.generateProcessApi(
        GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = bpmnXmls.mapIndexed { index, bpmnXml ->
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = bpmnXml, processName = "process-$index.bpmn")
            },
            packagePath = "de.gen",
            outputLanguage = OutputLanguage.JAVA,
            engine = ProcessEngine.ZEEBE,
        ),
    )

    private fun compileJava(generated: List<GeneratedApiFile>): List<String> {
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "JDK (not JRE) required to run this test" }
        val workDir = Files.createTempDirectory("nav-compile").toFile()
        val sourceFiles = generated.map { File(workDir, it.fileName).apply { writeText(it.content) } }
        val outDir = File(workDir, "out").apply { mkdirs() }

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
        // Reuse this JVM's classpath so the runtime interfaces (a test dependency) resolve during attribution.
        val options = listOf("-classpath", System.getProperty("java.class.path"), "-d", outDir.absolutePath)
        compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
        fileManager.close()

        return diagnostics.diagnostics
            .filter { it.kind == Diagnostic.Kind.ERROR }
            .map { "${it.lineNumber}: ${it.getMessage(null)}" }
    }
}
