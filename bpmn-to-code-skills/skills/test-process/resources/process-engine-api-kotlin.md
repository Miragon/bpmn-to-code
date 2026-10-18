# process-engine-api · Process test (Kotlin)

Applies to process-engine-api projects on an embedded C7-family adapter (Camunda 7, CIB seven, Operaton). `@SpringBootTest` boots the real engine, the adapter and the real `@ProcessEngineWorker` beans. The use cases are mocks, and the bpm-assert library of the engine checks the walked path. On Camunda 8 the engine is not embedded: use Camunda Process Test (`@CamundaSpringProcessTest`) and its assertions as in `zeebe-kotlin.md`. The workers, mocks and `ProcessPath` stay the same.

Contents: Test profile · Wait helper · Test

## Test profile

```yaml
# src/test/resources/application-test.yaml
camunda.bpm.job-execution.enabled: false
dev.bpm-crafters.process-api.adapter.cib-seven-embedded:
  service-tasks: { delivery-strategy: embedded_scheduled, schedule-delivery-fixed-rate-in-seconds: 1 }
  user-tasks: { delivery-strategy: embedded_scheduled, schedule-delivery-fixed-rate-in-seconds: 1 }
```

The job executor is off, so the test fires async continuations and timers itself. The workers poll for external tasks on the adapter's schedule. The adapter block is engine-specific: copy its keys from the main `application.yaml` (Operaton uses `operaton.bpm`). Test dependencies: the engine's bpm-assert (`org.cibseven.bpm:cibseven-bpm-assert`), H2, `com.ninja-squad:springmockk`.

## Wait helper

```kotlin
fun ProcessEngine.continueToNextWaitState(timeoutMillis: Long = 15_000) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var idleIterations = 0
    while (System.currentTimeMillis() < deadline) {
        val job = managementService.createJobQuery().active().messages().listPage(0, 1).firstOrNull()
        if (job != null) {
            managementService.executeJob(job.id)
            idleIterations = 0
            continue
        }
        val openExternalTasks = externalTaskService.createExternalTaskQuery().count()
        idleIterations = if (openExternalTasks == 0L) idleIterations + 1 else 0
        if (idleIterations >= 3) return
        Thread.sleep(200)
    }
}
```

It runs the parked async jobs and waits until the polling workers have drained every external task. Fire a timer through `managementService.createJobQuery().timers().activityId(FlowNodes.X.ELEMENT_ID).singleResult()` and `executeJob`.

## Test

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var process: LeasingProcess

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @MockkBean(relaxed = true)
    private lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @BeforeEach
    fun setUp() {
        init(processEngine)
        every { orderBikeUseCase.orderBike(any(), any()) } returns OrderId("ORDER-1")
    }

    @Test
    fun `contract signed - the bike is ordered and the process waits for the handover`() {
        val application = testLeasingApplication(id = ApplicationId.new())
        process.submitRequest(application)
        processEngine.continueToNextWaitState()

        process.correlateContractSigned(application.id)
        processEngine.continueToNextWaitState()

        val orderBranch = ProcessPath.from(FlowNodes.GatewayFork)
            .then { it.gatewayBikeSourceJoin }
            .then { it.serviceTaskOrderBike }
            .then { it.gatewayJoin }
        val instance = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.value)
            .processInstanceBusinessKey(application.id.value.toString())
            .singleResult()
        assertThat(instance)
            .isWaitingAt(FlowNodes.EventHandoverReported.ELEMENT_ID)
            .hasPassedInOrder(*orderBranch.ids)
        verify { orderBikeUseCase.orderBike(application.id, application.bikeId) }
    }
}
```

- Imports: `com.ninjasquad.springmockk.MockkBean`, `io.miragon.bpmn.runtime.path.ProcessPath` and the step extensions (`io.miragon.bpmn.runtime.path.then`, `onto`, `inside`, `interruptedBy`, …), and `assertThat` / `init` from `org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests` (Camunda 7: `org.camunda.bpm…`, Operaton: `org.operaton.bpm…`).
- Drive the instance only through the outbound port (`process.submitRequest`, `process.correlate…`) and the real workers. Call `continueToNextWaitState()` after every step.
- Mock every use case a worker calls. Relaxed mocks cover `Unit` methods. Stub results that decide a gateway, and stub with `throws` for an error boundary event.
- Use a fresh business key per test (`ApplicationId.new()`): all tests share one engine.
- For an unordered set (parallel branches, compensation handlers) use `hasPassed(*path.distinctIds)`.
- Delivery is at least once. With compensation or parallel work a use case may run twice, so verify with `atLeast = 1` there.
- `dev.bpm-crafters.process-engine-api-testing` (2026.10.1) offers engine-agnostic JGiven scenarios (`ProcessScenarioTest`, `processHasPassedInOrder(*path.ids)`). The blueprint does not use it.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.kt
