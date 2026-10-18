# Camunda 7 family, embedded · process test (Kotlin)

Use this when the engine runs inside the Spring Boot app. The test boots the app with the real engine on H2, the real delegates and the real process adapter, and mocks the use cases. The job executor is off, so the test drives every async continuation and timer itself.

Contents: `src/test/resources/application-test.yaml` · Helpers (test sources, next to the process tests) · Test · Notes

| Engine | Import root | Assert artifact | Config prefix |
|---|---|---|---|
| Camunda 7 | `org.camunda.bpm.engine` | `org.camunda.bpm:camunda-bpm-assert` | `camunda.bpm` |
| CIB seven | `org.cibseven.bpm.engine` | `org.cibseven.bpm:cibseven-bpm-assert` | `camunda.bpm` |
| Operaton | `org.operaton.bpm.engine` | `org.operaton.bpm:operaton-bpm-assert` | `operaton.bpm` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` | `org.finos.fluxnova.bpm:fluxnova-bpm-assert` | `fluxnova.bpm` |

Test dependencies: the assert artifact, `com.h2database:h2`, `com.ninja-squad:springmockk`, `io.miragon:bpmn-to-code-runtime`.

## `src/test/resources/application-test.yaml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:process-test;DB_CLOSE_DELAY=-1
camunda:            # prefix from the table
  bpm:
    database:
      schema-update: true
    job-execution:
      enabled: false
```

## Helpers (test sources, next to the process tests)

```kotlin
fun ProcessEngine.executeJobFor(node: FlowNode) {
    val job = managementService.createJobQuery().messages().activityId(node.id.value).singleResult()
    requireNotNull(job) { "no async-continuation job for '${node.id.value}'" }
    managementService.executeJob(job.id)
}

fun ProcessEngine.fireTimer(node: FlowNode) {
    val timer = managementService.createJobQuery().timers().activityId(node.id.value).singleResult()
    requireNotNull(timer) { "no timer job for '${node.id.value}'" }
    managementService.executeJob(timer.id)
}

fun ProcessEngine.continueToNextWaitState(maxJobs: Int = 50) {
    repeat(maxJobs) {
        val job = managementService.createJobQuery().active().messages().listPage(0, 1).firstOrNull() ?: return
        managementService.executeJob(job.id)
    }
}

fun RuntimeService.findProcessInstance(businessKey: String): ProcessInstance {
    val instance = createProcessInstanceQuery()
        .processDefinitionKey(BikeLeasingProcessApi.PROCESS_ID.value)
        .processInstanceBusinessKey(businessKey)
        .singleResult()
    return requireNotNull(instance) { "no instance with business key $businessKey" }
}
```

`FlowNode` is `io.miragon.bpmn.runtime.FlowNode`. `executeJobFor(X)` runs the job that `asyncBefore` or `asyncAfter` created on element X.

## Test

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired private lateinit var process: BikeLeasingProcessAdapter
    @Autowired private lateinit var runtimeService: RuntimeService
    @Autowired private lateinit var processEngine: ProcessEngine

    @MockkBean(relaxed = true) private lateinit var orderBikeUseCase: OrderBikeUseCase
    @MockkBean(relaxed = true) private lateinit var activateLeasingUseCase: ActivateLeasingUseCase
    @MockkBean(relaxed = true) private lateinit var sendContractUseCase: SendContractUseCase
    @MockkBean(relaxed = true) private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @BeforeEach
    fun setUp() {
        init(processEngine)
    }

    @Test
    fun `bike unavailable - an accepted alternative is re-ordered and the leasing becomes active`() {
        every { orderBikeUseCase.orderBike(any(), any()) } throws
            BikeUnavailableException(BikeId("BIKE-900")) andThen OrderId("ORDER-2")
        val application = testLeasingApplication(id = ApplicationId.new())
        process.startBikeLeasing(application)
        val instance = runtimeService.findProcessInstance(application.id.value.toString())

        processEngine.executeJobFor(FlowNodes.StartEventLeasingRequestReceived)
        processEngine.executeJobFor(FlowNodes.ServiceTaskSendContract)
        process.notifyContractSigned(application.id)
        processEngine.executeJobFor(FlowNodes.EventContractSigned)
        processEngine.executeJobFor(FlowNodes.ServiceTaskIssueInsurancePolicy)
        processEngine.executeJobFor(FlowNodes.ServiceTaskOrderBike)
        process.completeAlternativeClarification(application.id, alternativeFound = true, bikeId = BikeId("BIKE-ALT"))
        processEngine.continueToNextWaitState()
        process.notifyHandoverReported(application.id)
        processEngine.executeJobFor(FlowNodes.EventHandoverReported)
        processEngine.fireTimer(FlowNodes.EventWithdrawalPeriodElapsed)

        val orderBranch = ProcessPath.from(FlowNodes.GatewayFork)
            .then { it.gatewayBikeSourceJoin }
            .then { it.serviceTaskOrderBike }
            .interruptedBy(FlowNodes.ServiceTaskOrderBike) { it.eventBikeUnavailable }
            .then { it.userTaskClarifyAlternative }
            .then { it.gatewayAlternativeFound }
            .then { it.gatewayBikeSourceJoin }
            .then { it.serviceTaskOrderBike }
            .then { it.gatewayJoin }
            .then { it.eventHandoverReported }
            .then { it.eventWithdrawalPeriodElapsed }
            .then { it.endEventLeasingActive }
        assertThat(instance).isEnded.hasPassedInOrder(*orderBranch.ids)
        verify(exactly = 1) { activateLeasingUseCase.activate(application.id) }
    }
}
```

`init`, `assertThat` come from `<root>.test.assertions.bpmn.BpmnAwareTests`. `ProcessPath`, `then`, `interruptedBy` come from `io.miragon.bpmn.runtime.path`.

## Notes

- **Mock every use case a delegate on the walked path calls.** An unmocked one runs the real service against H2 and usually fails into an incident. Use relaxed mocks, and stub only the results that pick a branch.
- **Use a fresh business key per test** (`ApplicationId.new()`). The H2 engine outlives a single test, so `findProcessInstance` with a fixed key finds old instances and `singleResult()` throws.
- Wait for an element with `assertThat(instance).isWaitingAt(FlowNodes.X.ELEMENT_ID)`, and check variables with `.variables().containsEntry(...)`.
- `executeJobFor` failing with "no async-continuation job" means the token is elsewhere: a mock picked another branch, or the instance parked earlier. A delegate exception surfaces directly from `executeJob`.
- Parallel branches: walk each branch from the fork, then assert either each branch with `hasPassedInOrder`, or all of them unordered with `hasPassed(*nodesOf(a.nodes, b.nodes).map { it.id.value }.toTypedArray())`.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.kt
