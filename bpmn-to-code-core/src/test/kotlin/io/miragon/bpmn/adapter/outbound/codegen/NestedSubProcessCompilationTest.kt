package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
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
 * Regression gate for a subprocess nested inside another subprocess. The generated interior scope is named
 * `Inner`; if the interior nodes were nested inside it, a nested subprocess would emit an `Inner` enclosed by
 * another `Inner` — legal in Kotlin, but a compile error in Java (JLS 8.1.3). The existing golden tests only
 * *parse* the output, so they can't catch this; here we generate the Java API and actually **compile** it
 * against the runtime interfaces.
 */
class NestedSubProcessCompilationTest {

    private val service = GenerateProcessApiInMemoryService()

    @Test
    fun `generated java for nested subprocesses compiles`() {
        val generated = generate(OutputLanguage.JAVA)
        val errors = compileJava(generated.fileName, generated.content)
        assertThat(errors)
            .withFailMessage { "Generated Java did not compile:\n${errors.joinToString("\n")}" }
            .isEmpty()
    }

    private fun generate(language: OutputLanguage) =
        service.generateProcessApi(
            GenerateProcessApiInMemoryUseCase.Command(
                bpmnContents = listOf(
                    GenerateProcessApiInMemoryUseCase.BpmnInput(
                        bpmnXml = requireNotNull(javaClass.getResource("/bpmn/nested-subprocess.bpmn")).readText(),
                        processName = "nested-subprocess.bpmn",
                    ),
                ),
                packagePath = "de.gen",
                outputLanguage = language,
                engine = ProcessEngine.ZEEBE,
            ),
        ).single()

    private fun compileJava(fileName: String, source: String): List<String> {
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "JDK (not JRE) required to run this test" }
        val workDir = Files.createTempDirectory("nav-compile").toFile()
        val sourceFile = File(workDir, fileName).apply { writeText(source) }
        val outDir = File(workDir, "out").apply { mkdirs() }

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        val units = fileManager.getJavaFileObjectsFromFiles(listOf(sourceFile))
        // Reuse this JVM's classpath so the runtime interfaces (a test dependency) resolve during attribution.
        val options = listOf("-classpath", System.getProperty("java.class.path"), "-d", outDir.absolutePath)
        compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
        fileManager.close()

        return diagnostics.diagnostics
            .filter { it.kind == Diagnostic.Kind.ERROR }
            .map { "${it.lineNumber}: ${it.getMessage(null)}" }
    }
}
