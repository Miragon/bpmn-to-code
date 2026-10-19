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
 * The golden test only compares text. Here the generated TypeScript is handed to `tsc` with the strictest settings a
 * consuming project may use, in a Node project and in a bundler project, so that anything the generator emits
 * carelessly fails the build. The sources of `@miragon/bpmn-to-code-runtime` are installed into the project's
 * `node_modules`, so they are checked with the same settings.
 *
 * Skipped when no `tsc` is on the `PATH`; `npm ci` in `tools/` installs the pinned one, and CI puts it on the path.
 */
@EnabledIf("tscAvailable")
class TypeScriptCompilationTest {

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
    fun `generated typescript compiles`(bpmnResource: String, engine: ProcessEngine) {
        val generated = generate(listOf(requireNotNull(javaClass.getResource(bpmnResource)).readText()), engine)

        assertCompiles(project(generated, NODE_NEXT))
    }

    @Test
    fun `generated typescript with several sequence flows to the same element compiles`() {
        val generated = generate(listOf(CSharpCompilationTest.TWO_FLOWS_TO_ONE_TASK), ProcessEngine.ZEEBE)

        assertCompiles(project(generated, NODE_NEXT))
    }

    @ParameterizedTest
    @ValueSource(strings = [NODE_NEXT, BUNDLER])
    fun `two generated apis and consumer code compile together in one project`(compilerOptions: String) {
        // given: the APIs of one run plus code that uses them, as a Node project and as a bundler project
        val nested = requireNotNull(javaClass.getResource("/bpmn/nested-subprocess.bpmn")).readText()
        val projectDir = project(generate(listOf(bikeLeasing(), nested), ProcessEngine.ZEEBE), compilerOptions)
        File(projectDir, "consumer.ts").writeText(CONSUMER)

        assertCompiles(projectDir)
    }

    private fun generate(bpmnXmls: List<String>, engine: ProcessEngine): List<GeneratedApiFile> {
        val command = GenerateProcessApiInMemoryUseCase.Command(
            resources = bpmnXmls.mapIndexed { index, bpmnXml ->
                BpmnResource(fileName = "process-$index.bpmn", content = bpmnXml.encodeToByteArray())
            },
            packagePath = "",
            outputLanguage = OutputLanguage.TYPESCRIPT,
            engine = engine,
        )
        return service.generateProcessApi(command)
    }

    @Test
    fun `process path steps only compile for what the current node offers`() {
        val projectDir = project(generate(listOf(bikeLeasing()), ProcessEngine.ZEEBE), NODE_NEXT)
        File(projectDir, "steps.ts").writeText(UNCHECKED_STEPS)

        val result = compile(projectDir)

        val stepLines = UNCHECKED_STEPS.lines().withIndex().filter { it.value.trimStart().startsWith("ProcessPath.from") }.map { it.index + 1 }
        val failedLines = Regex("""steps\.ts\((\d+),""").findAll(result.output).map { it.groupValues[1].toInt() }.toSet()
        assertThat(failedLines).withFailMessage { result.output }.containsExactlyInAnyOrderElementsOf(stepLines)
    }

    private fun project(generated: List<GeneratedApiFile>, compilerOptions: String): File {
        val projectDir = Files.createTempDirectory("typescript-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "package.json").writeText("""{ "type": "module" }""")
        File(projectDir, "tsconfig.json").writeText(tsconfig(compilerOptions))
        installRuntime(projectDir.resolve("node_modules/@miragon/bpmn-to-code-runtime"))
        return projectDir
    }

    private fun installRuntime(target: File) {
        val sources = File(System.getProperty("typescript.runtime"), "src")
        sources.listFiles { file -> file.extension == "ts" }.orEmpty().forEach { it.copyTo(target.resolve("src/${it.name}")) }
        target.resolve("package.json").writeText(RUNTIME_PACKAGE_JSON)
    }

    private fun assertCompiles(projectDir: File) {
        val result = compile(projectDir)
        assertThat(result.exitCode).withFailMessage { "Generated TypeScript did not compile:\n${result.output}" }.isZero()
    }

    private fun compile(projectDir: File): ProcessResult {
        val process = ProcessBuilder("tsc", "-p", "tsconfig.json").directory(projectDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return ProcessResult(exitCode = -1, output = "tsc timed out after $TIMEOUT_MINUTES min\n$output")
        }
        return ProcessResult(process.exitValue(), output)
    }

    private fun bikeLeasing() = requireNotNull(javaClass.getResource("/bpmn/zeebe/bike-leasing.bpmn")).readText()

    private data class ProcessResult(val exitCode: Int, val output: String)

    companion object {

        private const val TIMEOUT_MINUTES = 2L

        private const val NODE_NEXT = """"module": "NodeNext", "moduleResolution": "NodeNext""""
        private const val BUNDLER = """"module": "ESNext", "moduleResolution": "bundler""""

        private val RUNTIME_PACKAGE_JSON = """
            {
              "name": "@miragon/bpmn-to-code-runtime",
              "type": "module",
              "exports": { ".": { "types": "./src/index.ts", "default": "./src/index.js" } }
            }
        """.trimIndent()

        private fun tsconfig(moduleOptions: String) = """
            {
              "compilerOptions": {
                "target": "ES2022",
                $moduleOptions,
                "strict": true,
                "noEmit": true,
                "noUncheckedIndexedAccess": true,
                "exactOptionalPropertyTypes": true,
                "noImplicitOverride": true,
                "noUnusedLocals": true,
                "noUnusedParameters": true,
                "noPropertyAccessFromIndexSignature": true,
                "verbatimModuleSyntax": true,
                "erasableSyntaxOnly": true,
                "isolatedModules": true
              },
              "include": ["*.ts"]
            }
        """.trimIndent()

        /**
         * What a consumer writes against the generated API: job types and messages from the shared constants,
         * navigation over `FlowNodes`, edge and facet reads typed by the runtime interfaces, and a `ProcessPath`.
         */
        val CONSUMER = """
            import { ProcessPath } from "@miragon/bpmn-to-code-runtime";
            import type * as Runtime from "@miragon/bpmn-to-code-runtime";
            import { BikeLeasingProcessApi as Api } from "./BikeLeasingProcessApi.js";
            import { NestedSubprocessProcessProcessApi } from "./NestedSubprocessProcessProcessApi.js";
            import { ServiceTasks } from "./ServiceTasks.js";

            export function describe(jobType: string): string {
              switch (jobType) {
                case ServiceTasks.MIRAVELO_SEND_CONTRACT:
                  return "contract";
                default:
                  return "other";
              }
            }

            export function navigate(): string {
              const start = Api.FlowNodes.StartEventLeasingRequestReceived;
              const successor = start.next.serviceTaskValidateApplication;
              const edge: Runtime.SequenceFlow<typeof Api.FlowNodes.ServiceTaskValidateApplication> = successor.flow;
              const condition: string | null = edge.conditionExpression;
              const jobTask: Runtime.HasJobType = Api.FlowNodes.ServiceTaskSendContract;
              const boundary: Runtime.BoundaryEvent = Api.FlowNodes.TimerSignatureReminder;
              const attached: Runtime.AttachedBoundaryEvent = Api.FlowNodes.SubProcessConcludeContract.next.timerSignatureReminder;
              const handler: Runtime.AssociatedCompensationHandler = Api.FlowNodes.BoundaryCompensateContract.next.serviceTaskCancelContract;
              const creditRating = successor.target.next.businessRuleTaskCheckCreditRating.target;
              const subProcess = creditRating.next.gatewayIsSolvent.target.next.subProcessConcludeContract.target;
              const innerName: string | null = subProcess.startEvents.startEventCustomerEligible.name;
              const variables: Runtime.VariableDefinitions = Api.FlowNodes.ServiceTaskSendContract.variables;
              const input: Runtime.VariableName = Api.FlowNodes.ServiceTaskSendContract.variables.APPLICATION_ID;
              const mapping: Runtime.InputOutputMapping = Api.FlowNodes.CallActivityCancelBikeOrder.Inputs.ORDER_IDS;
              const engine: Runtime.BpmnEngine = Api.PROCESS_ENGINE;
              const all: readonly Runtime.FlowNode[] = [...Api.FlowNodes.all, ...NestedSubprocessProcessProcessApi.FlowNodes.all];
              return [condition, jobTask.jobType, boundary.attachedTo.id, attached.target.id, handler.target.id, innerName,
                variables.all.length, input.value, mapping.target, engine, all.length].join(" ");
            }

            export function walk(): string[] {
              const rejected: ProcessPath<typeof Api.FlowNodes.EndEventApplicationRejected> = ProcessPath.from(Api.FlowNodes.GatewayIsSolvent)
                .onto((next) => next.subProcessConcludeContract)
                .inside((contract) => contract.enter((start) => start.startEventCustomerEligible).then((next) => next.serviceTaskSendContract))
                .then((next) => next.boundaryContractNotSigned)
                .then((next) => next.gatewayCollectRejections)
                .then((next) => next.serviceTaskSendRejection)
                .then((next) => next.endEventApplicationRejected);
              return ProcessPath.from(Api.FlowNodes.StartEventApplicationWithdrawn)
                .then((next) => next.eventReverseApplication)
                .throwingCompensation(Api.FlowNodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract)
                .then((next) => next.serviceTaskSendCancellationConfirmation)
                .ids.concat(rejected.ids);
            }
        """.trimIndent()

        /**
         * One step per line that must not compile: each names something the current node does not offer.
         */
        val UNCHECKED_STEPS = """
            import { ProcessPath } from "@miragon/bpmn-to-code-runtime";
            import { BikeLeasingProcessApi as Api } from "./BikeLeasingProcessApi.js";

            export const steps = [
              ProcessPath.from(Api.FlowNodes.EndEventLeasingActive).then((next) => next),
              ProcessPath.from(Api.FlowNodes.StartEventLeasingRequestReceived).then((next) => next.gatewayFork),
              ProcessPath.from(Api.FlowNodes.GatewayFork).enter((start) => start),
              ProcessPath.from(Api.FlowNodes.GatewayFork).throwingCompensation(Api.FlowNodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract),
            ];
        """.trimIndent()

        @JvmStatic
        fun tscAvailable(): Boolean = runCatching {
            ProcessBuilder("tsc", "--version")
                .redirectErrorStream(true).start().also { it.waitFor(1, TimeUnit.MINUTES) }.exitValue() == 0
        }.getOrDefault(false)
    }
}
