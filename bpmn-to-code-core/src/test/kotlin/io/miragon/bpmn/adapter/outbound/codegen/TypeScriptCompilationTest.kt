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
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@EnabledIf("typeScriptCompilerAvailable")
class TypeScriptCompilationTest {

    private val service = GenerateProcessApiInMemoryService()

    private val runtimePackage: File by lazy { buildRuntimePackage() }

    @ParameterizedTest
    @CsvSource(
        "/bpmn/zeebe/bike-leasing.bpmn, ZEEBE, NODE_NEXT",
        "/bpmn/c7/bike-leasing.bpmn, CAMUNDA_7, NODE_NEXT",
        "/bpmn/operaton/bike-leasing.bpmn, OPERATON, NODE_NEXT",
        "/bpmn/zeebe/membership.bpmn, ZEEBE, NODE_NEXT",
        "/bpmn/nested-subprocess.bpmn, ZEEBE, NODE_NEXT",
        "/bpmn/zeebe/bike-leasing.bpmn, ZEEBE, BUNDLER",
    )
    fun `generated typescript type-checks`(bpmnResource: String, engine: ProcessEngine, resolution: ModuleResolution) {
        val projectDir = project(resolution, generate(listOf(bpmnResource), engine))

        assertTypeChecks(projectDir)
    }

    @ParameterizedTest
    @EnumSource(ModuleResolution::class)
    fun `two generated apis and consumer code type-check together`(resolution: ModuleResolution) {
        // given: two APIs of one run sharing their definition files, plus code that reads both
        val generated = generate(listOf("/bpmn/zeebe/bike-leasing.bpmn", "/bpmn/zeebe/cancel-bike-order.bpmn"), ProcessEngine.ZEEBE)
        val projectDir = project(resolution, generated)
        File(projectDir, "consumer.ts").writeText(CONSUMER)

        // when / then: the compiler accepts the generated files and the literal types the consumer relies on
        assertTypeChecks(projectDir)
    }

    @ParameterizedTest
    @CsvSource(
        "/bpmn/zeebe/bike-leasing.bpmn, ZEEBE",
        "/bpmn/nested-subprocess.bpmn, ZEEBE",
    )
    @EnabledIf("nodeAvailable")
    fun `every successor, start element and host resolves to a node of FlowNodes once the module is loaded`(bpmnResource: String, engine: ProcessEngine) {
        // given: the generated files compiled to JavaScript, plus a script that follows every getter of every node
        val projectDir = project(ModuleResolution.NODE_NEXT, generate(listOf(bpmnResource), engine))
        val apiFile = projectDir.listFiles().orEmpty().single { it.name.endsWith("ProcessApi.ts") }.nameWithoutExtension
        File(projectDir, "walk.ts").writeText(walkScript(apiFile))
        assertTypeChecks(projectDir, "--noEmit", "false", "--outDir", "out")
        File(projectDir, "out/package.json").writeText("""{ "type": "module" }""")

        // when: node runs the script
        val result = run(projectDir, "node", "out/walk.js")

        // then: no getter leads outside FlowNodes
        assertThat(result.exitCode).withFailMessage { "The generated navigation is broken:\n${result.output}" }.isZero()
        assertThat(result.output).contains("checked")
    }

    @Test
    @EnabledIf("nodeAvailable")
    fun `process paths walk the bike-leasing scenarios the JVM runtime is tested with`() {
        // given: the generated files compiled to JavaScript, plus the scenarios of the JVM runtime's path tests
        val projectDir = project(ModuleResolution.NODE_NEXT, generate(listOf("/bpmn/zeebe/bike-leasing.bpmn"), ProcessEngine.ZEEBE))
        File(projectDir, "scenarios.ts").writeText(PROCESS_PATH_SCENARIOS)
        assertTypeChecks(projectDir, "--noEmit", "false", "--outDir", "out")
        File(projectDir, "out/package.json").writeText("""{ "type": "module" }""")

        // when: node runs the scenarios
        val result = run(projectDir, "node", "out/scenarios.js")

        // then: every scenario recorded the expected elements and sequence flows
        assertThat(result.exitCode).withFailMessage { "Process path scenarios failed:\n${result.output}" }.isZero()
        assertThat(result.output).contains("all process path scenarios passed")
    }

    private fun generate(bpmnResources: List<String>, engine: ProcessEngine): List<GeneratedApiFile> {
        val resources = bpmnResources.mapIndexed { index, resource ->
            val content = requireNotNull(javaClass.getResource(resource)).readBytes()
            BpmnResource(fileName = "process-$index.bpmn", content = content)
        }
        val command = GenerateProcessApiInMemoryUseCase.Command(
            resources = resources,
            packagePath = "process",
            outputLanguage = OutputLanguage.TYPESCRIPT,
            engine = engine,
        )
        return service.generateProcessApi(command)
    }

    private fun project(resolution: ModuleResolution, generated: List<GeneratedApiFile>): File {
        val projectDir = Files.createTempDirectory("typescript-compile").toFile()
        generated.forEach { File(projectDir, it.fileName).writeText(it.content) }
        File(projectDir, "tsconfig.json").writeText(tsconfig(resolution))
        File(projectDir, "package.json").writeText("""{ "type": "module" }""")
        runtimePackage.copyRecursively(File(projectDir, "node_modules/@miragon/bpmn-to-code-runtime"))
        return projectDir
    }

    private fun buildRuntimePackage(): File {
        val sources = File(requireNotNull(System.getProperty("typescript.runtime.package")))
        val built = Files.createTempDirectory("typescript-runtime").toFile()
        File(sources, "package.json").copyTo(File(built, "package.json"))
        val result = run(sources, compilerPath(), "--project", "tsconfig.json", "--outDir", File(built, "dist").absolutePath)
        check(result.exitCode == 0) { "The runtime package did not build:\n${result.output}" }
        return built
    }

    private fun assertTypeChecks(projectDir: File, vararg extraArguments: String) {
        val result = run(projectDir, compilerPath(), "--project", "tsconfig.json", *extraArguments)
        assertThat(result.exitCode).withFailMessage { "Generated TypeScript did not type-check:\n${result.output}" }.isZero()
    }

    private fun run(directory: File, vararg command: String): ProcessResult {
        val process = ProcessBuilder(*command).directory(directory).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return ProcessResult(exitCode = -1, output = "timed out after $TIMEOUT_MINUTES min\n$output")
        }
        return ProcessResult(exitCode = process.exitValue(), output = output)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    enum class ModuleResolution(val module: String, val moduleResolution: String) {
        NODE_NEXT(module = "nodenext", moduleResolution = "nodenext"),
        BUNDLER(module = "preserve", moduleResolution = "bundler"),
    }

    companion object {

        private const val TIMEOUT_MINUTES = 2L

        private fun compilerPath(): String = System.getProperty("typescript.compiler").orEmpty()

        private fun tsconfig(resolution: ModuleResolution) = """
            {
              "compilerOptions": {
                "target": "es2022",
                "module": "${resolution.module}",
                "moduleResolution": "${resolution.moduleResolution}",
                "strict": true,
                "noEmit": true,
                "noUnusedLocals": true,
                "declaration": true,
                "verbatimModuleSyntax": true
              },
              "include": ["*.ts"]
            }
        """.trimIndent()

        private val CONSUMER = """
            import { BikeLeasingProcessApi } from "./BikeLeasingProcessApi.js";
            import { CancelBikeOrderProcessApi } from "./CancelBikeOrderProcessApi.js";
            import { Messages } from "./Messages.js";
            import { ProcessPath, type FlowNode, type VariableName } from "@miragon/bpmn-to-code-runtime";
            import { ServiceTasks } from "./ServiceTasks.js";

            const nodes = BikeLeasingProcessApi.FlowNodes;
            const processId: "bikeLeasing" = BikeLeasingProcessApi.PROCESS_ID;
            const sendContract = nodes.ServiceTaskSendContract;
            const jobType: typeof ServiceTasks.MIRAVELO_SEND_CONTRACT = sendContract.jobType;
            const label: string | null = sendContract.name;
            const contractId: VariableName<"OUTPUT"> = sendContract.variables.CONTRACT_ID;
            const calledProcess: typeof CancelBikeOrderProcessApi.PROCESS_ID = nodes.CallActivityCancelBikeOrder.calledProcess;
            const everyNode: readonly FlowNode[] = nodes.all;
            const everyMessage: readonly string[] = Messages.all;

            function workerFor(type: (typeof ServiceTasks)[Exclude<keyof typeof ServiceTasks, "all">]): string {
              return type;
            }

            // @ts-expect-error: an output is not an input
            const notAnInput: VariableName<"INPUT"> = sendContract.variables.CONTRACT_ID;

            const solvent = nodes.StartEventLeasingRequestReceived
              .next.serviceTaskValidateApplication.target
              .next.businessRuleTaskCheckCreditRating.target
              .next.gatewayIsSolvent.target;
            const concluded = solvent.next.subProcessConcludeContract;
            const approvedName: "Conclude contract" = concluded.target.name;
            const isDefault: boolean = concluded.flow.isDefault;
            const reminder: "attachedBoundaryEvent" = concluded.target.next.timerSignatureReminder.kind;
            const host: typeof nodes.SubProcessConcludeContract = nodes.TimerSignatureReminder.attachedTo;

            // @ts-expect-error: the gateway has no successor of that name
            solvent.next.gatewayFork;

            // @ts-expect-error: the chain skips the credit rating
            nodes.StartEventLeasingRequestReceived.next.serviceTaskValidateApplication.target.next.gatewayIsSolvent;

            const walked: string[] = ProcessPath.from(solvent).then((next) => next.subProcessConcludeContract).ids;

            // @ts-expect-error: an end event has no successors
            ProcessPath.from(nodes.EndEventLeasingActive).then((next) => next.gatewayFork);

            // @ts-expect-error: a gateway has no interior to enter
            ProcessPath.from(solvent).enter((start) => start.startEventCustomerEligible);

            // @ts-expect-error: a gateway has no interior to walk
            ProcessPath.from(solvent).inside((scope) => scope);

            // @ts-expect-error: only an event that throws a compensation records one
            ProcessPath.from(solvent).throwingCompensation(nodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract);

            // @ts-expect-error: the unchecked jump needs the explicit opt-in
            ProcessPath.from(solvent).jumpTo(nodes.GatewayFork);

            // @ts-expect-error: the reminder timer is a boundary event, not a compensation handler
            ProcessPath.from(nodes.EventReverseApplication).throwingCompensation(nodes.SubProcessConcludeContract, (next) => next.timerSignatureReminder);

            export const used = [
              processId,
              jobType,
              label,
              contractId,
              calledProcess,
              everyNode,
              everyMessage,
              workerFor(jobType),
              notAnInput,
              approvedName,
              isDefault,
              reminder,
              host,
              walked,
            ];
        """.trimIndent()

        private fun walkScript(apiName: String) = """
            import { $apiName as Api } from "./$apiName.js";
            import type { FlowNode } from "@miragon/bpmn-to-code-runtime";

            type Reached = { kind: string; target: FlowNode; flows?: readonly { kind: string; target: FlowNode }[] };
            const nodes = Api.FlowNodes as unknown as Record<string, FlowNode>;
            const everyNode = new Set(Api.FlowNodes.all);
            let checked = 0;
            for (const [key, node] of Object.entries(nodes)) {
              if (key === "all") continue;
              if (node.constructor.name !== key || !everyNode.has(node)) throw new Error(`FlowNodes.${'$'}{key} is not its node`);
              const { next, startEvents, attachedTo } = node as { next?: Record<string, Reached>; startEvents?: Record<string, FlowNode>; attachedTo?: FlowNode };
              for (const [name, successor] of Object.entries(next ?? {})) {
                if (!everyNode.has(successor.target)) throw new Error(`${'$'}{key}.next.${'$'}{name} leads outside FlowNodes`);
                if (successor.flows?.some((flow) => flow.kind !== "sequenceFlow" || flow.target !== successor.target)) {
                  throw new Error(`${'$'}{key}.next.${'$'}{name}: a flow leads elsewhere`);
                }
                checked++;
              }
              for (const [name, start] of Object.entries(startEvents ?? {})) {
                if (!everyNode.has(start)) throw new Error(`${'$'}{key}.startEvents.${'$'}{name} is not in FlowNodes`);
                checked++;
              }
              if (attachedTo !== undefined && !everyNode.has(attachedTo)) throw new Error(`${'$'}{key}.attachedTo is not in FlowNodes`);
            }
            if (everyNode.size !== Object.keys(nodes).length - 1) throw new Error("all lists other nodes than FlowNodes");
            console.log(`checked ${'$'}{checked} successors and start elements`);
        """.trimIndent()

        private val PROCESS_PATH_SCENARIOS = """
            import { BikeLeasingProcessApi } from "./BikeLeasingProcessApi.js";
            import { nodesOf, ProcessPath, RiskyNavigation } from "@miragon/bpmn-to-code-runtime";

            const nodes = BikeLeasingProcessApi.FlowNodes;

            const happyPath = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
              .then((next) => next.serviceTaskValidateApplication)
              .then((next) => next.businessRuleTaskCheckCreditRating)
              .then((next) => next.gatewayIsSolvent)
              .onto((next) => next.subProcessConcludeContract)
              .inside((contract) => contract
                .enter((start) => start.startEventCustomerEligible)
                .then((next) => next.serviceTaskSendContract)
                .then((next) => next.gatewayAwaitSignature)
                .then((next) => next.eventContractSigned)
                .then((next) => next.endEventContractConcluded))
              .then((next) => next.gatewayFork)
              .then((next) => next.serviceTaskOrderBike)
              .then((next) => next.gatewayJoin)
              .then((next) => next.receiveTaskHandoverReported)
              .then((next) => next.timerWithdrawalPeriodElapsed)
              .then((next) => next.endEventLeasingActive);
            expect("happy path", happyPath.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "gateway_isSolvent", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "event_contractSigned", "endEvent_contractConcluded", "gateway_fork", "serviceTask_orderBike", "gateway_join", "receiveTask_handoverReported", "timer_withdrawalPeriodElapsed", "endEvent_leasingActive"]);

            const escalation = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
              .then((next) => next.serviceTaskValidateApplication)
              .then((next) => next.businessRuleTaskCheckCreditRating)
              .enter(nodes.SubProcessConcludeContract, (start) => start.startEventCustomerEligible)
              .then((next) => next.serviceTaskSendContract)
              .then((next) => next.gatewayAwaitSignature)
              .then((next) => next.timerSignatureDeadline)
              .then((next) => next.endEventContractNotSigned)
              .interruptedBy(nodes.SubProcessConcludeContract, (next) => next.boundaryContractNotSigned)
              .then((next) => next.gatewayCollectRejections)
              .then((next) => next.serviceTaskSendRejection)
              .then((next) => next.endEventApplicationRejected);
            expect("escalation", escalation.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "timer_signatureDeadline", "endEvent_contractNotSigned", "boundary_contractNotSigned", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected"]);

            const errorBoundary = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
              .then((next) => next.serviceTaskValidateApplication)
              .then((next) => next.boundaryApplicationInvalid)
              .then((next) => next.gatewayCollectRejections)
              .then((next) => next.serviceTaskSendRejection)
              .then((next) => next.endEventApplicationRejected);
            expect("error boundary", errorBoundary.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "boundary_applicationInvalid", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected"]);

            const reminder = ProcessPath.from(nodes.GatewayIsSolvent)
              .onto((next) => next.subProcessConcludeContract)
              .then((next) => next.timerSignatureReminder)
              .then((next) => next.serviceTaskSendReminderMail)
              .then((next) => next.endEventCustomerReminded);
            expect("reminder", reminder.ids, ["gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded"]);

            const flows = ProcessPath.from(nodes.BusinessRuleTaskCheckCreditRating)
              .then((next) => next.gatewayIsSolvent)
              .then((next) => next.subProcessConcludeContract);
            expect("flow ids", flows.ids, ["businessRuleTask_checkCreditRating", "gateway_isSolvent", "subProcess_concludeContract"]);
            expect("flows", flows.flowIds, ["flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract"]);

            const compensated = ProcessPath.from(nodes.StartEventApplicationWithdrawn)
              .then((next) => next.eventReverseApplication)
              .throwingCompensation(nodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract, { includeBoundaryEvent: true })
              .throwingCompensation(nodes.BoundaryCompensateOrder, (next) => next.callActivityCancelBikeOrder, { includeBoundaryEvent: true })
              .then((next) => next.serviceTaskSendCancellationConfirmation)
              .then((next) => next.endEventApplicationCancelled);
            expect("compensation", compensated.ids, ["startEvent_applicationWithdrawn", "event_reverseApplication", "boundary_compensateContract", "serviceTask_cancelContract", "boundary_compensateOrder", "callActivity_cancelBikeOrder", "serviceTask_sendCancellationConfirmation", "endEvent_applicationCancelled"]);
            expect("compensation flows", compensated.flowIds, ["flow_applicationWithdrawnToReverseApplication", "flow_reverseApplicationToSendCancellationConfirmation", "flow_sendCancellationConfirmationToApplicationCancelled"]);

            const withoutBoundary = ProcessPath.from(nodes.StartEventApplicationWithdrawn)
              .then((next) => next.eventReverseApplication)
              .throwingCompensation(nodes.BoundaryCompensateInsurance, (next) => next.serviceTaskCancelPolicy)
              .then((next) => next.serviceTaskSendCancellationConfirmation);
            expect("compensation without boundary", withoutBoundary.ids, ["startEvent_applicationWithdrawn", "event_reverseApplication", "serviceTask_cancelPolicy", "serviceTask_sendCancellationConfirmation"]);

            const orderBranch = ProcessPath.from(nodes.GatewayFork)
              .then((next) => next.serviceTaskOrderBike)
              .then((next) => next.gatewayJoin)
              .then((next) => next.receiveTaskHandoverReported).nodes;
            const insuranceBranch = ProcessPath.from(nodes.GatewayFork)
              .then((next) => next.serviceTaskIssueInsurancePolicy)
              .then((next) => next.gatewayJoin)
              .then((next) => next.receiveTaskHandoverReported).nodes;
            const parallel = nodesOf(orderBranch, insuranceBranch).map((node) => node.id);
            if (new Set(parallel).size !== parallel.length || !parallel.includes("serviceTask_orderBike") || !parallel.includes("serviceTask_issueInsurancePolicy")) {
              throw new Error("parallel branches");
            }

            const jumped = ProcessPath.from(nodes.GatewayFork)
              .then((next) => next.serviceTaskOrderBike)
              .jumpTo(RiskyNavigation, nodes.GatewayFork)
              .then((next) => next.serviceTaskIssueInsurancePolicy)
              .then((next) => next.gatewayJoin)
              .then((next) => next.receiveTaskHandoverReported);
            expect("jump", jumped.ids, ["gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join", "receiveTask_handoverReported"]);

            const repeated = ProcessPath.from(nodes.GatewayFork).thenMultipleTimes(3, (next) => next.serviceTaskOrderBike);
            expect("repeated", repeated.ids, ["gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike", "serviceTask_orderBike"]);
            expect("repeated distinct", repeated.distinctIds, ["gateway_fork", "serviceTask_orderBike"]);

            const picked = ProcessPath.from(nodes.GatewayIsSolvent).then((next) => next.subProcessConcludeContract.flow);
            expect("picked flow", picked.flowIds, ["flow_isSolventToConcludeContract"]);

            console.log("all process path scenarios passed");

            function expect(scenario: string, actual: readonly string[], expected: readonly string[]): void {
              if (actual.join() !== expected.join()) {
                throw new Error(`${'$'}{scenario}: expected [${'$'}{expected.join(", ")}] but walked [${'$'}{actual.join(", ")}]`);
              }
            }
        """.trimIndent()

        @JvmStatic
        fun typeScriptCompilerAvailable(): Boolean = File(compilerPath()).canExecute()

        @JvmStatic
        fun nodeAvailable(): Boolean = runCatching {
            ProcessBuilder("node", "--version").start().waitFor(1, TimeUnit.MINUTES)
        }.getOrDefault(false)
    }
}
