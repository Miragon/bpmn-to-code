package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.BpmnResource
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
        "/bpmn/zeebe/bike-leasing.bpmn, ZEEBE",
        "/bpmn/c7/bike-leasing.bpmn, CAMUNDA_7",
        "/bpmn/operaton/bike-leasing.bpmn, OPERATON",
        "/bpmn/zeebe/membership.bpmn, ZEEBE",
        "/bpmn/c7/membership.bpmn, CAMUNDA_7",
        "/bpmn/nested-subprocess.bpmn, ZEEBE",
    )
    fun `generated csharp compiles`(bpmnResource: String, engine: ProcessEngine) {
        val projectDir = project(csproj(), generate(listOf(bpmnResource), engine))

        assertCompiles(projectDir)
    }

    @Test
    fun `generated csharp of two processes sharing job types and messages compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/zeebe/bike-leasing.bpmn")).readText()
        val copy = bpmnXml.replace("id=\"bikeLeasing\"", "id=\"bikeLeasingCopy\"")
        val projectDir = project(csproj(), generateFromXml(listOf(bpmnXml, copy), ProcessEngine.ZEEBE))

        assertCompiles(projectDir)
    }

    @Test
    fun `generated csharp with several sequence flows to the same element compiles`() {
        val generated = generateFromXml(listOf(TWO_FLOWS_TO_ONE_TASK), ProcessEngine.ZEEBE)
        assertThat(generated.single { it.processId == "approval" }.content)
            .contains(
                "public $RUNTIME.SequenceFlows<TaskApprove> TaskApprove => new(FlowNodes.TaskApprove.Instance, new $RUNTIME.SequenceFlow<TaskApprove>[] {",
            )

        assertCompiles(project(csproj(), generated))
    }

    @Test
    fun `generated csharp with variables named like the members of their holder compiles`() {
        val bpmnXml = requireNotNull(javaClass.getResource("/bpmn/zeebe/bike-leasing.bpmn")).readText()
        val renamed = bpmnXml.replace("target=\"bikeIds\"", "target=\"inputs\"").replace("target=\"age\"", "target=\"outputs\"")
        val generated = generateFromXml(listOf(renamed), ProcessEngine.ZEEBE)
        assertThat(generated.single { it.processId == "bikeLeasing" }.content).contains(" Inputs_ { get; }", " Outputs_ { get; }")

        assertCompiles(project(csproj(), generated))
    }

    @ParameterizedTest
    @ValueSource(strings = ["<Nullable>disable</Nullable>", "<GenerateDocumentationFile>true</GenerateDocumentationFile>"])
    fun `generated csharp compiles whatever the consuming project's nullable and documentation settings`(setting: String) {
        val projectDir = project(csproj(setting), generate(listOf("/bpmn/zeebe/bike-leasing.bpmn"), ProcessEngine.ZEEBE))

        assertCompiles(projectDir)
    }

    @Test
    fun `two generated apis and consumer code compile together in one project`() {
        // given: two APIs of one run in the same namespace, plus code that uses them through the runtime package
        val projectDir = project(
            csproj(),
            generate(listOf("/bpmn/zeebe/bike-leasing.bpmn", "/bpmn/nested-subprocess.bpmn"), ProcessEngine.ZEEBE),
        )
        File(projectDir, "Consumer.cs").writeText(CONSUMER)

        assertCompiles(projectDir)
    }

    @Test
    fun `process path steps only compile for what the current node offers`() {
        val projectDir = project(csproj(), generate(listOf("/bpmn/zeebe/bike-leasing.bpmn"), ProcessEngine.ZEEBE))
        File(projectDir, "Steps.cs").writeText(UNCHECKED_STEPS)

        val result = runDotnetBuild(projectDir)

        val stepLines = UNCHECKED_STEPS.lines().withIndex().filter { it.value.trimStart().startsWith("ProcessPath.From") }.map { it.index + 1 }
        val failedLines = Regex("""Steps\.cs\((\d+),""").findAll(result.output).map { it.groupValues[1].toInt() }.toSet()
        assertThat(failedLines).withFailMessage { result.output }.containsExactlyInAnyOrderElementsOf(stepLines)
    }

    private fun generate(bpmnResources: List<String>, engine: ProcessEngine) = generateFromXml(
        bpmnResources.map { requireNotNull(javaClass.getResource(it)).readText() },
        engine,
    )

    private fun generateFromXml(bpmnXmls: List<String>, engine: ProcessEngine) = service.generateProcessApi(
        GenerateProcessApiInMemoryUseCase.Command(
            resources = bpmnXmls.mapIndexed { index, bpmnXml ->
                BpmnResource(fileName = "process-$index.bpmn", content = bpmnXml.encodeToByteArray())
            },
            packagePath = "De.Gen",
            outputLanguage = OutputLanguage.CSHARP,
            engine = engine,
        ),
    )

    /**
     * The runtime package is copied in and referenced as a project, so the build sees it as a separate assembly, the
     * way a consumer's build sees the NuGet package, and needs nothing outside the project directory.
     */
    private fun project(csproj: String, generated: List<GeneratedApiFile>): File {
        val projectDir = Files.createTempDirectory("csharp-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "generated.csproj").writeText(csproj)
        copyRuntimeInto(File(projectDir, RUNTIME_DIRECTORY))
        return projectDir
    }

    private fun copyRuntimeInto(target: File) {
        val runtime = File(System.getProperty("csharp.runtime"))
        runtime.walkTopDown()
            .onEnter { it.name !in setOf("bin", "obj") }
            .filter { it.isFile && it.extension in setOf("cs", "csproj") }
            .forEach { it.copyTo(target.resolve(it.relativeTo(runtime)), overwrite = true) }
        val packagesRoot = runtime.parentFile.parentFile
        packagesRoot.resolve("Directory.Build.props").copyTo(target.resolve("Directory.Build.props"))
    }

    private fun assertCompiles(projectDir: File) {
        val result = runDotnetBuild(projectDir)
        assertThat(result.exitCode).withFailMessage { "Generated C# did not compile:\n${result.output}" }.isZero()
    }

    private fun runDotnetBuild(projectDir: File): ProcessResult {
        val process = ProcessBuilder("dotnet", "build", "--nologo", "-v", "q")
            .directory(projectDir).redirectErrorStream(true).start()
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
        private const val RUNTIME_DIRECTORY = "runtime"
        private const val RUNTIME = "global::Miragon.BpmnToCode.Runtime"

        private fun csproj(extraSetting: String = "") = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
                <DefaultItemExcludes>${'$'}(DefaultItemExcludes);$RUNTIME_DIRECTORY/**</DefaultItemExcludes>
                $extraSetting
              </PropertyGroup>
              <ItemGroup>
                <ProjectReference Include="$RUNTIME_DIRECTORY/Miragon.BpmnToCode.Runtime.csproj" />
              </ItemGroup>
            </Project>
        """.trimIndent()

        /**
         * A gateway whose two conditional flows both lead to the same task, so one `SequenceFlows` carries both.
         */
        val TWO_FLOWS_TO_ONE_TASK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="approval" isExecutable="true">
                <bpmn:startEvent id="startEvent"><bpmn:outgoing>flow_start</bpmn:outgoing></bpmn:startEvent>
                <bpmn:exclusiveGateway id="gateway_amount" default="flow_else">
                  <bpmn:incoming>flow_start</bpmn:incoming>
                  <bpmn:outgoing>flow_small</bpmn:outgoing>
                  <bpmn:outgoing>flow_vip</bpmn:outgoing>
                  <bpmn:outgoing>flow_else</bpmn:outgoing>
                </bpmn:exclusiveGateway>
                <bpmn:task id="task_approve">
                  <bpmn:incoming>flow_small</bpmn:incoming>
                  <bpmn:incoming>flow_vip</bpmn:incoming>
                  <bpmn:outgoing>flow_approved</bpmn:outgoing>
                </bpmn:task>
                <bpmn:endEvent id="endEvent_approved"><bpmn:incoming>flow_approved</bpmn:incoming></bpmn:endEvent>
                <bpmn:endEvent id="endEvent_manual"><bpmn:incoming>flow_else</bpmn:incoming></bpmn:endEvent>
                <bpmn:sequenceFlow id="flow_start" sourceRef="startEvent" targetRef="gateway_amount" />
                <bpmn:sequenceFlow id="flow_small" sourceRef="gateway_amount" targetRef="task_approve">
                  <bpmn:conditionExpression>=amount &lt; 100</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="flow_vip" sourceRef="gateway_amount" targetRef="task_approve">
                  <bpmn:conditionExpression>=customer.isVip</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="flow_else" sourceRef="gateway_amount" targetRef="endEvent_manual" />
                <bpmn:sequenceFlow id="flow_approved" sourceRef="task_approve" targetRef="endEvent_approved" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()

        /**
         * What a consumer writes against the generated API: attribute arguments and switch labels from the
         * constants, navigation over `FlowNodes`, edge and facet reads through the runtime interfaces, and a
         * `ProcessPath` whose steps only compile for real successors — all of which must resolve and compile.
         */
        val CONSUMER = """
            using System;
            using De.Gen;
            using Miragon.BpmnToCode.Runtime;
            using Miragon.BpmnToCode.Runtime.Path;
            using Api = De.Gen.BikeLeasingProcessApi;

            namespace De.Gen.Consumer;

            [AttributeUsage(AttributeTargets.Method, AllowMultiple = true)]
            public sealed class JobTypeAttribute : Attribute
            {
                public JobTypeAttribute(string type) => Type = type;
                public string Type { get; }
            }

            public static class Consumer
            {
                [JobType(ServiceTasks.MiraveloSendContract)]
                public static string Describe(string jobType)
                {
                    switch (jobType)
                    {
                        case ServiceTasks.MiraveloSendContract:
                            return "contract";
                        default:
                            return "other";
                    }
                }

                public static void Navigate()
                {
                    var start = Api.FlowNodes.StartEventLeasingRequestReceived.Instance;
                    var successor = start.Next.ServiceTaskValidateApplication;
                    var edge = successor.Flow;
                    string? condition = edge.ConditionExpression;
                    bool isDefault = edge.IsDefault;
                    Api.FlowNodes.ServiceTaskValidateApplication target = edge.Target;
                    ISequenceFlow generic = edge;
                    IHasJobType jobTask = Api.FlowNodes.ServiceTaskSendContract.Instance;
                    string jobType = jobTask.JobType;
                    if (!ReferenceEquals(generic.Target, target)) throw new InvalidOperationException();
                    if (!edge.Equals(start.Next.ServiceTaskValidateApplication.Flow)) throw new InvalidOperationException();
                    ISuccessor<IFlowNode> genericSuccessor = successor;
                    IBoundaryEvent<Api.FlowNodes.SubProcessConcludeContract> boundary = Api.FlowNodes.TimerSignatureReminder.Instance;
                    IBoundaryEvent<IFlowNode> anyBoundary = boundary;
                    AttachedBoundaryEvent<Api.FlowNodes.TimerSignatureReminder> attached = Api.FlowNodes.SubProcessConcludeContract.Instance.Next.TimerSignatureReminder;
                    ICompensationThrowEvent thrower = Api.FlowNodes.EventReverseApplication.Instance;
                    AssociatedCompensationHandler<Api.FlowNodes.ServiceTaskCancelContract> handler = Api.FlowNodes.BoundaryCompensateContract.Instance.Next.ServiceTaskCancelContract;
                    IHasSuccessors<object> withSuccessors = start;
                    IFlowScope<object> scope = Api.FlowNodes.SubProcessConcludeContract.Instance;

                    var creditRating = successor.Target.Next.BusinessRuleTaskCheckCreditRating.Target;
                    var subProcess = creditRating.Next.GatewayIsSolvent.Target.Next.SubProcessConcludeContract.Target;
                    var innerStart = subProcess.Start.StartEventCustomerEligible;
                    string? innerName = innerStart.Name;
                    string hostId = Api.FlowNodes.TimerSignatureReminder.Instance.AttachedTo.Id.Value;
                    bool interrupts = Api.FlowNodes.TimerSignatureReminder.Instance.IsInterrupting;
                    VariableName.Input input = Api.FlowNodes.ServiceTaskSendContract.Instance.Variables.ApplicationId;
                    IHasVariables withVariables = Api.FlowNodes.ServiceTaskSendContract.Instance;
                    int declared = withVariables.Variables.All.Count + withVariables.Variables.Inputs.Count + withVariables.Variables.Outputs.Count;
                    if (declared == 0) throw new InvalidOperationException();
                    ProcessId called = Api.FlowNodes.CallActivityCancelBikeOrder.Instance.CalledProcess;
                    InputOutputMapping mapping = Api.FlowNodes.CallActivityCancelBikeOrder.Instance.Inputs.OrderIds;
                    MessageName message = Messages.MiraveloContractSigned;
                    BpmnErrorDefinition error = Errors.MiraveloApplicationInvalid;

                    ElementId other = NestedSubprocessProcessProcessApi.FlowNodes.StartEventRoot.Instance.Id;
                    Console.WriteLine($"{condition} {isDefault} {innerName} {hostId} {interrupts} {input} {called} {mapping} {other} {anyBoundary.Id} {genericSuccessor.Target.Id} {attached.Target.Id} {handler.Target.Id} {thrower.Id} {withSuccessors.Id} {scope.Id} {message} {error.Code}");
                }

                public static string[] Walk()
                {
                    IProcessPath<Api.FlowNodes.EndEventApplicationCancelled> compensated = ProcessPath.From(Api.FlowNodes.StartEventApplicationWithdrawn.Instance)
                        .Then(next => next.EventReverseApplication)
                        .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateContract.Instance, next => next.ServiceTaskCancelContract, includeBoundaryEvent: true)
                        .Then(next => next.ServiceTaskSendCancellationConfirmation)
                        .Then(next => next.EndEventApplicationCancelled);
                    IProcessPath<Api.FlowNodes.EndEventApplicationRejected> rejected = ProcessPath.From(Api.FlowNodes.GatewayIsSolvent.Instance)
                        .Onto(next => next.SubProcessConcludeContract)
                        .Inside(contract => contract.Enter(start => start.StartEventCustomerEligible).Then(next => next.ServiceTaskSendContract))
                        .Then(next => next.BoundaryContractNotSigned)
                        .Then(next => next.GatewayCollectRejections)
                        .Then(next => next.ServiceTaskSendRejection)
                        .Then(next => next.EndEventApplicationRejected);
                    return compensated.Ids;
                }
            }
        """.trimIndent()

        /**
         * One step per line that must not compile: each names something the current node does not offer, or jumps
         * without opting in.
         */
        val UNCHECKED_STEPS = """
            using Miragon.BpmnToCode.Runtime.Path;
            using Api = De.Gen.BikeLeasingProcessApi;

            namespace De.Gen.Steps;

            public static class Steps
            {
                public static void Unchecked()
                {
                    ProcessPath.From(Api.FlowNodes.EndEventLeasingActive.Instance).Then(next => next);
                    ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived.Instance).Then(next => next.GatewayFork);
                    ProcessPath.From(Api.FlowNodes.GatewayFork.Instance).Enter(start => start);
                    ProcessPath.From(Api.FlowNodes.GatewayFork.Instance).ThrowingCompensation(Api.FlowNodes.BoundaryCompensateContract.Instance, next => next.ServiceTaskCancelContract);
                    ProcessPath.From(Api.FlowNodes.GatewayFork.Instance).JumpTo(Api.FlowNodes.GatewayJoin.Instance);
                }
            }
        """.trimIndent()

        @JvmStatic
        fun dotnetAvailable(): Boolean = runCatching {
            ProcessBuilder("dotnet", "--version")
                .redirectErrorStream(true).start().also { it.waitFor(1, TimeUnit.MINUTES) }.exitValue() == 0
        }.getOrDefault(false)
    }
}
