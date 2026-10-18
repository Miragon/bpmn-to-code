# Camunda 7 family, remote · process test (Kotlin)

Applies when the service owns the model but the engine runs elsewhere (external tasks, engine REST API). The test runs a standalone in-memory engine without Spring and drives it by topic. Workers and the process adapter speak REST and cannot attach to it, so the test completes each external task with the output its worker returns and starts or correlates through `RuntimeService` with the same generated names; `/test-worker` and `/test-process-adapter` cover the mapping.

| Family | Engine and assertions | `MockExpressionManager`, `Mocks` | Test dependencies |
|---|---|---|---|
| CIB seven | `org.cibseven.bpm.engine…` | `org.cibseven.bpm.engine.test.mock` | `cibseven-engine`, `cibseven-bpm-assert`, `h2` |
| Operaton | `org.operaton.bpm.engine…` | `org.operaton.bpm.engine.impl.mock` | `operaton-engine`, `operaton-bpm-assert`, `h2` |
| Camunda 7 | `org.camunda.bpm.engine…` | `org.camunda.bpm.engine.test.mock` | `camunda-engine`, `camunda-bpm-assert`, `h2` |

## Helpers (copy once into the test sources)

```kotlin
private const val TEST_WORKER = "test-worker"

fun ProcessEngine.startByMessage(message: MessageName, businessKey: String, variables: Map<String, Any?>): ProcessInstance {
    val instance = runtimeService.startProcessInstanceByMessage(message.value, businessKey, variables)
    executeAsyncContinuations()
    return instance
}

fun ProcessEngine.completeExternalTask(topic: String, variables: Map<String, Any?> = emptyMap()) {
    externalTaskService.complete(lockSingleExternalTask(topic).id, TEST_WORKER, variables)
    executeAsyncContinuations()
}

fun ProcessEngine.raiseBpmnErrorOnExternalTask(topic: String, error: BpmnErrorDefinition) {
    externalTaskService.handleBpmnError(lockSingleExternalTask(topic).id, TEST_WORKER, error.code)
    executeAsyncContinuations()
}

fun ProcessEngine.correlateMessage(message: MessageName, businessKey: String) {
    runtimeService.createMessageCorrelation(message.value).processInstanceBusinessKey(businessKey).correlate()
    executeAsyncContinuations()
}

fun ProcessEngine.fireTimer(timerEvent: FlowNode) {
    val timer = managementService.createJobQuery().timers().activityId(timerEvent.id.value).singleResult()
    managementService.executeJob(requireNotNull(timer) { "no timer job for '${timerEvent.id.value}'" }.id)
    executeAsyncContinuations()
}

private fun ProcessEngine.lockSingleExternalTask(topic: String): ExternalTask {
    val tasks = externalTaskService.createExternalTaskQuery().topicName(topic).notLocked().list()
    require(tasks.size == 1) { "expected one waiting external task for '$topic', found ${tasks.size}" }
    externalTaskService.lock(tasks.single().id, TEST_WORKER, 60_000L)
    return tasks.single()
}

private fun ProcessEngine.executeAsyncContinuations() {
    repeat(100) {
        val job = managementService.createJobQuery().active().messages().listPage(0, 1).firstOrNull() ?: return
        managementService.executeJob(job.id)
    }
    error("async continuations did not settle")
}
```

## Test

```kotlin
class BikeLeasingProcessTest {

    private lateinit var engine: ProcessEngine

    @BeforeEach
    fun setUp() {
        engine = StandaloneInMemProcessEngineConfiguration().apply {
            jdbcUrl = "jdbc:h2:mem:process-${UUID.randomUUID()};DB_CLOSE_DELAY=1000"
            expressionManager = MockExpressionManager()
        }.buildProcessEngine()
        Mocks.register("bikeOrderAuditListener", ExecutionListener { })
        Mocks.register("clarifyAlternativeTaskListener", TaskListener { })
        engine.repositoryService.createDeployment()
            .addClasspathResource("bpmn/bike-leasing.bpmn")
            .addClasspathResource("dmn/check-credit-rating.dmn")
            .deploy()
        init(engine)
    }

    @AfterEach
    fun tearDown() {
        Mocks.reset()
        engine.close()
    }

    @Test
    fun `bike unavailable - order is interrupted and waits for clarification`() {
        val businessKey = UUID.randomUUID().toString()
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        val variables = mapOf(
            start.APPLICATION_ID.value to businessKey, start.BIKE_ID.value to "BIKE-900",
            start.MONTHLY_NET_INCOME.value to 3500.0, start.AGE.value to 35,
        )
        val instance = engine.startByMessage(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED, businessKey, variables)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        engine.raiseBpmnErrorOnExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE)

        val path = ProcessPath.from(FlowNodes.GatewayFork)
            .then { it.gatewayBikeSourceJoin }
            .then { it.serviceTaskOrderBike }
            .interruptedBy(FlowNodes.ServiceTaskOrderBike) { it.eventBikeUnavailable }
        assertThat(instance)
            .isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
            .hasPassedInOrder(*path.ids)
    }
}
```

- Register every bean the model references by expression (`delegateExpression`, listeners, `${bean.method()}`) with `Mocks.register`; external tasks need none. Deploy called processes and DMN tables the scenario reaches.
- Every helper settles async continuations, so the next external task exists when the next line runs. Timers stay untouched until `fireTimer(FlowNodes.X)` fires one regardless of its due date. A none start event starts with `startProcessInstanceByKey(<Process>ProcessApi.PROCESS_ID.value, …)`.
- `hasPassedInOrder` (from `BpmnAwareTests`) only sees finished activities: end the path before the wait state and assert that with `isWaitingAt`. Path steps (`then`, `interruptedBy`, …) are imported from `io.miragon.bpmn.runtime.path`.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/kotlin-gradle/service/example-service/src/test/kotlin/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.kt
