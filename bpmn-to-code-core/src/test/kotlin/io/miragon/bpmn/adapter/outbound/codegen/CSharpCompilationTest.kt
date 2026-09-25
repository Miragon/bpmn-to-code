package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * The golden test only compares text, so on its own it cannot tell valid C# from a plausible-looking
 * string. Here the generated API is handed to the real C# compiler, with warnings escalated to errors so
 * that anything the generator emits carelessly — a shadowed nested type, a bad escape — fails the build.
 *
 * Skipped when no .NET SDK is installed, which keeps the suite runnable for contributors without one; CI
 * installs the SDK so this does gate pull requests.
 */
@EnabledIf("dotnetAvailable")
class CSharpCompilationTest {

    private val service = GenerateProcessApiInMemoryService()

    @ParameterizedTest
    @CsvSource(
        "/bpmn/c8-subscribe-newsletter.bpmn, ZEEBE",
        "/bpmn/c7-subscribe-newsletter.bpmn, CAMUNDA_7",
        "/bpmn/operaton-subscribe-newsletter.bpmn, OPERATON",
        "/bpmn/nested-subprocess.bpmn, ZEEBE",
    )
    fun `generated csharp compiles`(bpmnResource: String, engine: ProcessEngine) {
        val bpmnXml = requireNotNull(javaClass.getResource(bpmnResource)).readText()
        assertCompiles(generate(listOf(bpmnXml), engine))
    }

    @Test
    fun `generated csharp of two processes sharing job types and messages compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn")).readText()
        val copy = bpmnXml.replace("id=\"newsletterSubscription\"", "id=\"newsletterSubscriptionCopy\"")
        assertCompiles(generate(listOf(bpmnXml, copy), ProcessEngine.ZEEBE))
    }

    private fun assertCompiles(generated: List<GeneratedApiFile>) {
        val projectDir = Files.createTempDirectory("csharp-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "generated.csproj").writeText(CSPROJ)

        val result = runDotnetBuild(projectDir)

        assertThat(result.exitCode)
            .withFailMessage { "Generated C# did not compile:\n${result.output}" }
            .isZero()
    }

    private fun generate(bpmnXmls: List<String>, engine: ProcessEngine) = service.generateProcessApi(
        GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = bpmnXmls.mapIndexed { index, bpmnXml ->
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = bpmnXml, processName = "process-$index.bpmn")
            },
            packagePath = "De.Gen",
            outputLanguage = OutputLanguage.CSHARP,
            engine = engine,
        ),
    )

    private fun runDotnetBuild(projectDir: File): ProcessResult {
        val process = ProcessBuilder("dotnet", "build", "--nologo", "-v", "q")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return ProcessResult(exitCode = -1, output = "dotnet build timed out after $BUILD_TIMEOUT_MINUTES min\n$output")
        }
        return ProcessResult(process.exitValue(), output)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    companion object {

        private const val BUILD_TIMEOUT_MINUTES = 5L

        private val CSPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
              </PropertyGroup>
            </Project>
        """.trimIndent()

        @JvmStatic
        fun dotnetAvailable(): Boolean = runCatching {
            ProcessBuilder("dotnet", "--version")
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor(1, TimeUnit.MINUTES) }
                .exitValue() == 0
        }.getOrDefault(false)
    }
}
