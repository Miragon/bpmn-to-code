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
import org.junit.jupiter.params.provider.ValueSource
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
        val projectDir = project(csproj(), generate(listOf(bpmnResource), engine))

        assertCompiles(projectDir)
    }

    @Test
    fun `generated csharp of two processes sharing job types and messages compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/c8-subscribe-newsletter.bpmn")).readText()
        val copy = bpmnXml.replace("id=\"newsletterSubscription\"", "id=\"newsletterSubscriptionCopy\"")
        val projectDir = project(csproj(), generateFromXml(listOf(bpmnXml, copy), ProcessEngine.ZEEBE))

        assertCompiles(projectDir)
    }

    @ParameterizedTest
    @ValueSource(strings = ["<Nullable>disable</Nullable>", "<GenerateDocumentationFile>true</GenerateDocumentationFile>"])
    fun `generated csharp compiles whatever the consuming project's nullable and documentation settings`(setting: String) {
        val projectDir = project(csproj(setting), generate(listOf("/bpmn/c8-subscribe-newsletter.bpmn"), ProcessEngine.ZEEBE))

        assertCompiles(projectDir)
    }

    @Test
    fun `two generated apis and consumer code compile together in one project`() {
        // given: two APIs of one run in the same namespace, each inlining its own runtime types, plus code that uses them
        val projectDir = project(
            csproj(),
            generate(listOf("/bpmn/c8-subscribe-newsletter.bpmn", "/bpmn/nested-subprocess.bpmn"), ProcessEngine.ZEEBE),
        )
        File(projectDir, "Consumer.cs").writeText(CONSUMER)

        assertCompiles(projectDir)
    }

    private fun generate(bpmnResources: List<String>, engine: ProcessEngine) = generateFromXml(
        bpmnResources.map { requireNotNull(javaClass.getResource(it)).readText() },
        engine,
    )

    private fun generateFromXml(bpmnXmls: List<String>, engine: ProcessEngine) = service.generateProcessApi(
        GenerateProcessApiInMemoryUseCase.Command(
            bpmnContents = bpmnXmls.mapIndexed { index, bpmnXml ->
                GenerateProcessApiInMemoryUseCase.BpmnInput(bpmnXml = bpmnXml, processName = "process-$index.bpmn")
            },
            packagePath = "De.Gen",
            outputLanguage = OutputLanguage.CSHARP,
            engine = engine,
        ),
    )

    private fun project(csproj: String, generated: List<GeneratedApiFile>): File {
        val projectDir = Files.createTempDirectory("csharp-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "generated.csproj").writeText(csproj)
        return projectDir
    }

    private fun assertCompiles(projectDir: File) {
        val result = runDotnetBuild(projectDir)
        assertThat(result.exitCode)
            .withFailMessage { "Generated C# did not compile:\n${result.output}" }
            .isZero()
    }

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

        private fun csproj(extraSetting: String = "") = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
                $extraSetting
              </PropertyGroup>
            </Project>
        """.trimIndent()

        /**
         * What a consumer writes against the generated API: attribute arguments and switch labels from the
         * constants, navigation over `Flow`, and edge / facet reads — all of which must resolve and compile.
         */
        val CONSUMER = """
            using System;
            using De.Gen;
            using Api = De.Gen.NewsletterSubscriptionProcessApi;

            namespace De.Gen.Consumer;

            [AttributeUsage(AttributeTargets.Method, AllowMultiple = true)]
            public sealed class JobTypeAttribute : Attribute
            {
                public JobTypeAttribute(string type) => Type = type;
                public string Type { get; }
            }

            public static class Consumer
            {
                [JobType(ServiceTasks.NewsletterSendConfirmationMail)]
                [JobType(Api.Flow.ServiceTaskSendConfirmationMail.JobType)]
                public static string Describe(string jobType)
                {
                    switch (jobType)
                    {
                        case ServiceTasks.NewsletterSendConfirmationMail:
                            return "confirmation";
                        default:
                            return "other";
                    }
                }

                public static void Navigate()
                {
                    var start = Api.Flow.StartEventSubmitRegistrationForm.Instance;
                    var edge = start.Flows.FlowSubmitToIncrementCounter;
                    string? condition = edge.ConditionExpression;
                    bool isDefault = edge.IsDefault;
                    Api.Flow.ServiceTaskIncrementSubscriptionCounter target = edge.Target;
                    Api.Runtime.ISequenceFlow generic = edge;
                    if (!ReferenceEquals(generic.Target, target)) throw new InvalidOperationException();
                    if (!edge.Equals(start.Flows.FlowSubmitToIncrementCounter)) throw new InvalidOperationException();

                    var counter = start.Next.ServiceTaskIncrementSubscriptionCounter;
                    var subProcess = counter.Next.SubProcessConfirmation;
                    var innerStart = subProcess.Start.StartEventRequestReceived;
                    string? innerName = innerStart.Name;
                    string hostId = Api.Flow.TimerEveryDay.Instance.AttachedTo.Id.Value;
                    bool interrupts = Api.Flow.TimerEveryDay.Instance.IsInterrupting;
                    Api.Runtime.VariableName.Input input = Api.Flow.ServiceTaskSendConfirmationMail.Instance.Variables.SubscriptionId;
                    Api.Runtime.ProcessId called = Api.Flow.CallActivityAbortRegistration.Instance.CalledProcess;
                    Api.Runtime.InputOutputMapping mapping = Api.Flow.CallActivityAbortRegistration.Instance.Inputs.SubscriptionId;

                    NestedSubprocessProcessProcessApi.Runtime.ElementId other = NestedSubprocessProcessProcessApi.Flow.StartEventRoot.Instance.Id;
                    Console.WriteLine($"{condition} {isDefault} {innerName} {hostId} {interrupts} {input} {called} {mapping} {other}");
                }
            }
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
