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
            .contains("public global::Miragon.BpmnToCode.Runtime.SequenceFlows<TaskApproveNode> TaskApprove => _taskApprove ??= new(FlowNodes.TaskApprove, new global::Miragon.BpmnToCode.Runtime.SequenceFlow<TaskApproveNode>[] {")

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
        // given: two APIs of one run in the same namespace on the runtime package, plus code that uses them
        val projectDir = project(
            csproj(),
            generate(listOf("/bpmn/zeebe/bike-leasing.bpmn", "/bpmn/nested-subprocess.bpmn"), ProcessEngine.ZEEBE),
        )
        File(projectDir, "Consumer.cs").writeText(CONSUMER)

        assertCompiles(projectDir)
    }

    @Test
    fun `process paths walk the bike-leasing scenarios the JVM runtime is tested with`() {
        // given: the generated API in a console project whose program walks the scenarios and checks their ids
        val projectDir = project(csproj("<OutputType>Exe</OutputType>"), generate(listOf("/bpmn/zeebe/bike-leasing.bpmn"), ProcessEngine.ZEEBE))
        File(projectDir, "Program.cs").writeText(PROCESS_PATH_SCENARIOS)

        // when: the program runs
        val result = runDotnet(projectDir, "run", "--nologo", "-v", "q")

        // then: every scenario recorded the expected elements and sequence flows
        assertThat(result.exitCode).withFailMessage { "Process path scenarios failed:\n${result.output}" }.isZero()
        assertThat(result.output).contains("all process path scenarios passed")
    }

    @Test
    fun `a process path the model does not contain does not compile`() {
        // given: a walk from the solvency gateway straight to the fork, which no sequence flow connects
        val projectDir = project(csproj(), generate(listOf("/bpmn/zeebe/bike-leasing.bpmn"), ProcessEngine.ZEEBE))
        File(projectDir, "Walk.cs").writeText(IMPOSSIBLE_PATH)

        // when: the project is built
        val result = runDotnetBuild(projectDir)

        // then: the compiler rejects the missing successor
        assertThat(result.exitCode).isNotZero()
        assertThat(result.output).contains("CS1061", "GatewayFork")
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

    private fun project(csproj: String, generated: List<GeneratedApiFile>): File {
        val projectDir = Files.createTempDirectory("csharp-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "generated.csproj").writeText(csproj)
        val runtimeSources = runtimeProject().listFiles { file -> file.isFile }.orEmpty()
        runtimeSources.forEach { it.copyTo(File(projectDir, "$RUNTIME_DIRECTORY/${it.name}")) }
        return projectDir
    }

    private fun assertCompiles(projectDir: File) {
        val result = runDotnetBuild(projectDir)
        assertThat(result.exitCode).withFailMessage { "Generated C# did not compile:\n${result.output}" }.isZero()
    }

    private fun runDotnetBuild(projectDir: File): ProcessResult = runDotnet(projectDir, "build", "--nologo", "-v", "q")

    private fun runDotnet(projectDir: File, vararg arguments: String): ProcessResult {
        val process = ProcessBuilder("dotnet", *arguments)
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

        private fun runtimeProject(): File = File(requireNotNull(System.getProperty("csharp.runtime.project")))

        private fun csproj(extraSetting: String = "") = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
                $extraSetting
              </PropertyGroup>
              <ItemGroup>
                <Compile Remove="$RUNTIME_DIRECTORY/**" />
                <ProjectReference Include="$RUNTIME_DIRECTORY/BpmnToCode.Runtime.csproj" />
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
         * constants, navigation over `FlowNodes`, and edge / facet reads — all of which must resolve and compile.
         */
        val CONSUMER = """
            using System;
            using De.Gen;
            using Miragon.BpmnToCode.Runtime;
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
                    var start = Api.FlowNodes.StartEventLeasingRequestReceived;
                    var successor = start.Next.ServiceTaskValidateApplication;
                    var edge = successor.Flow;
                    string? condition = edge.ConditionExpression;
                    bool isDefault = edge.IsDefault;
                    Api.FlowNodes.ServiceTaskValidateApplicationNode target = edge.Target;
                    ISequenceFlow generic = edge;
                    IHasJobType jobTask = Api.FlowNodes.ServiceTaskSendContract;
                    string jobType = jobTask.JobType;
                    if (!ReferenceEquals(generic.Target, target)) throw new InvalidOperationException();
                    if (!edge.Equals(start.Next.ServiceTaskValidateApplication.Flow)) throw new InvalidOperationException();
                    ISuccessor<IFlowNode> genericSuccessor = successor;
                    IBoundaryEvent boundary = Api.FlowNodes.TimerSignatureReminder;
                    AttachedBoundaryEvent<Api.FlowNodes.TimerSignatureReminderNode> attached = Api.FlowNodes.SubProcessConcludeContract.Next.TimerSignatureReminder;
                    ICompensationThrowEvent thrower = Api.FlowNodes.EventReverseApplication;
                    AssociatedCompensationHandler<Api.FlowNodes.ServiceTaskCancelContractNode> handler = Api.FlowNodes.BoundaryCompensateContract.Next.ServiceTaskCancelContract;

                    var creditRating = successor.Target.Next.BusinessRuleTaskCheckCreditRating.Target;
                    var subProcess = creditRating.Next.GatewayIsSolvent.Target.Next.SubProcessConcludeContract.Target;
                    var innerStart = subProcess.Start.StartEventCustomerEligible;
                    string? innerName = innerStart.Name;
                    string hostId = Api.FlowNodes.TimerSignatureReminder.AttachedTo.Id.Value;
                    bool interrupts = Api.FlowNodes.TimerSignatureReminder.IsInterrupting;
                    VariableName.Input input = Api.FlowNodes.ServiceTaskSendContract.Variables.ApplicationId;
                    IHasVariables withVariables = Api.FlowNodes.ServiceTaskSendContract;
                    int declared = withVariables.Variables.All.Count + withVariables.Variables.Inputs.Count + withVariables.Variables.Outputs.Count;
                    if (declared == 0) throw new InvalidOperationException();
                    ProcessId called = Api.FlowNodes.CallActivityCancelBikeOrder.CalledProcess;
                    InputOutputMapping mapping = Api.FlowNodes.CallActivityCancelBikeOrder.Inputs.OrderIds;

                    ElementId other = NestedSubprocessProcessProcessApi.FlowNodes.StartEventRoot.Id;
                    IFlowNode[] acrossProcesses = { Api.FlowNodes.ServiceTaskSendContract, NestedSubprocessProcessProcessApi.FlowNodes.StartEventRoot };
                    string rawId = Api.FlowNodes.ServiceTaskSendContract.Id;
                    string rawProcess = Api.FlowNodes.CallActivityCancelBikeOrder.CalledProcess;
                    if (acrossProcesses.Length != 2 || rawId.Length == 0 || rawProcess.Length == 0) throw new InvalidOperationException();
                    if (!ReferenceEquals(start.Next, start.Next)) throw new InvalidOperationException("Next allocates on every read");
                    if (!ReferenceEquals(start.Next.ServiceTaskValidateApplication, start.Next.ServiceTaskValidateApplication)) throw new InvalidOperationException("a successor allocates on every read");
                    if (!ReferenceEquals(Api.FlowNodes.SubProcessConcludeContract.Start, Api.FlowNodes.SubProcessConcludeContract.Start)) throw new InvalidOperationException("Start allocates on every read");
                    Console.WriteLine($"{condition} {isDefault} {innerName} {hostId} {interrupts} {input} {called} {mapping} {other} {boundary.Id} {genericSuccessor.Target.Id} {attached.Target.Id} {handler.Target.Id} {thrower.Id}");
                }
            }
        """.trimIndent()

        /**
         * The bike-leasing scenarios of the JVM runtime's ProcessPathKotlinApiTest, walked with the C# process path.
         */
        val PROCESS_PATH_SCENARIOS = """
            using System;
            using System.Linq;
            using Miragon.BpmnToCode.Runtime;
            using Api = De.Gen.BikeLeasingProcessApi;

            var nodes = Api.FlowNodes.All;

            var happyPath = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
                .Then(n => n.ServiceTaskValidateApplication)
                .Then(n => n.BusinessRuleTaskCheckCreditRating)
                .Then(n => n.GatewayIsSolvent)
                .Onto(n => n.SubProcessConcludeContract)
                .Inside(contract => contract
                    .Enter(start => start.StartEventCustomerEligible)
                    .Then(n => n.ServiceTaskSendContract)
                    .Then(n => n.GatewayAwaitSignature)
                    .Then(n => n.EventContractSigned)
                    .End(n => n.EndEventContractConcluded))
                .Then(n => n.GatewayFork)
                .Then(n => n.ServiceTaskOrderBike)
                .Then(n => n.GatewayJoin)
                .Then(n => n.ReceiveTaskHandoverReported)
                .Then(n => n.TimerWithdrawalPeriodElapsed)
                .End(n => n.EndEventLeasingActive);
            Expect("happy path", happyPath.Ids, "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "gateway_isSolvent", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "event_contractSigned", "endEvent_contractConcluded", "gateway_fork", "serviceTask_orderBike", "gateway_join", "receiveTask_handoverReported", "timer_withdrawalPeriodElapsed", "endEvent_leasingActive");

            var escalation = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
                .Then(n => n.ServiceTaskValidateApplication)
                .Then(n => n.BusinessRuleTaskCheckCreditRating)
                .Enter(Api.FlowNodes.SubProcessConcludeContract, start => start.StartEventCustomerEligible)
                .Then(n => n.ServiceTaskSendContract)
                .Then(n => n.GatewayAwaitSignature)
                .Then(n => n.TimerSignatureDeadline)
                .End(n => n.EndEventContractNotSigned)
                .InterruptedBy(Api.FlowNodes.SubProcessConcludeContract, n => n.BoundaryContractNotSigned)
                .Then(n => n.GatewayCollectRejections)
                .Then(n => n.ServiceTaskSendRejection)
                .End(n => n.EndEventApplicationRejected);
            Expect("escalation", escalation.Ids, "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "timer_signatureDeadline", "endEvent_contractNotSigned", "boundary_contractNotSigned", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected");

            var errorBoundary = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
                .Then(n => n.ServiceTaskValidateApplication)
                .Then(n => n.BoundaryApplicationInvalid)
                .Then(n => n.GatewayCollectRejections)
                .Then(n => n.ServiceTaskSendRejection)
                .End(n => n.EndEventApplicationRejected);
            Expect("error boundary", errorBoundary.Ids, "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "boundary_applicationInvalid", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected");

            var reminder = ProcessPath.From(Api.FlowNodes.GatewayIsSolvent)
                .Onto(n => n.SubProcessConcludeContract)
                .Then(n => n.TimerSignatureReminder)
                .Then(n => n.ServiceTaskSendReminderMail)
                .End(n => n.EndEventCustomerReminded);
            Expect("reminder", reminder.Ids, "gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded");

            var flows = ProcessPath.From(Api.FlowNodes.BusinessRuleTaskCheckCreditRating)
                .Then(n => n.GatewayIsSolvent)
                .Then(n => n.SubProcessConcludeContract);
            Expect("flows ids", flows.Ids, "businessRuleTask_checkCreditRating", "gateway_isSolvent", "subProcess_concludeContract");
            Expect("flows", flows.FlowIds, "flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract");

            var compensated = ProcessPath.From(Api.FlowNodes.StartEventApplicationWithdrawn)
                .Then(n => n.EventReverseApplication)
                .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateContract, n => n.ServiceTaskCancelContract, includeBoundaryEvent: true)
                .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateOrder, n => n.CallActivityCancelBikeOrder, includeBoundaryEvent: true)
                .Then(n => n.ServiceTaskSendCancellationConfirmation)
                .End(n => n.EndEventApplicationCancelled);
            Expect("compensation", compensated.Ids, "startEvent_applicationWithdrawn", "event_reverseApplication", "boundary_compensateContract", "serviceTask_cancelContract", "boundary_compensateOrder", "callActivity_cancelBikeOrder", "serviceTask_sendCancellationConfirmation", "endEvent_applicationCancelled");
            Expect("compensation flows", compensated.FlowIds, "flow_applicationWithdrawnToReverseApplication", "flow_reverseApplicationToSendCancellationConfirmation", "flow_sendCancellationConfirmationToApplicationCancelled");

            var withoutBoundary = ProcessPath.From(Api.FlowNodes.StartEventApplicationWithdrawn)
                .Then(n => n.EventReverseApplication)
                .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateInsurance, n => n.ServiceTaskCancelPolicy)
                .Then(n => n.ServiceTaskSendCancellationConfirmation);
            Expect("compensation without boundary", withoutBoundary.Ids, "startEvent_applicationWithdrawn", "event_reverseApplication", "serviceTask_cancelPolicy", "serviceTask_sendCancellationConfirmation");

            var orderBranch = ProcessPath.From(Api.FlowNodes.GatewayFork).Then(n => n.ServiceTaskOrderBike).Then(n => n.GatewayJoin).Then(n => n.ReceiveTaskHandoverReported).Nodes;
            var insuranceBranch = ProcessPath.From(Api.FlowNodes.GatewayFork).Then(n => n.ServiceTaskIssueInsurancePolicy).Then(n => n.GatewayJoin).Then(n => n.ReceiveTaskHandoverReported).Nodes;
            var parallel = ProcessPath.NodesOf(orderBranch, insuranceBranch).Select(node => node.Id.Value).ToArray();
            if (parallel.Distinct().Count() != parallel.Length || !parallel.Contains("serviceTask_orderBike") || !parallel.Contains("serviceTask_issueInsurancePolicy")) throw new InvalidOperationException("parallel branches");

            #pragma warning disable BPMNTOCODE001
            var jumped = ProcessPath.From(Api.FlowNodes.GatewayFork)
                .Then(n => n.ServiceTaskOrderBike)
                .JumpTo(Api.FlowNodes.GatewayFork)
                .Then(n => n.ServiceTaskIssueInsurancePolicy)
                .Then(n => n.GatewayJoin)
                .Then(n => n.ReceiveTaskHandoverReported);
            #pragma warning restore BPMNTOCODE001
            Expect("jump", jumped.Ids, "gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join", "receiveTask_handoverReported");

            Console.WriteLine($"all process path scenarios passed over {nodes.Count} nodes");

            static void Expect(string scenario, string[] actual, params string[] expected)
            {
                if (!actual.SequenceEqual(expected))
                {
                    throw new InvalidOperationException($"{scenario}: expected [{string.Join(", ", expected)}] but walked [{string.Join(", ", actual)}]");
                }
            }
        """.trimIndent()

        val IMPOSSIBLE_PATH = """
            using Miragon.BpmnToCode.Runtime;
            using Api = De.Gen.BikeLeasingProcessApi;

            namespace De.Gen.Consumer;

            public static class Walk
            {
                public static string[] Ids() => ProcessPath.From(Api.FlowNodes.GatewayIsSolvent).Then(n => n.GatewayFork).Ids;
            }
        """.trimIndent()

        @JvmStatic
        fun dotnetAvailable(): Boolean = runCatching {
            ProcessBuilder("dotnet", "--version")
                .redirectErrorStream(true).start().also { it.waitFor(1, TimeUnit.MINUTES) }.exitValue() == 0
        }.getOrDefault(false)
    }
}
