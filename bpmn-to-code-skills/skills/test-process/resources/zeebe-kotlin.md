# Zeebe · Process test (Kotlin)

Applies to Camunda 8 projects with `io.camunda:camunda-process-test-spring` (Camunda Process Test). The Spring context runs with the real workers and the real process adapter against a test engine; the use cases are MockK beans.

## Runtime

The default runtime mode `managed` starts Camunda in a container through Testcontainers, so the test needs Docker or Podman; the blueprint uses it. For Podman, point `DOCKER_HOST` at the Podman socket (`podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}'`); `TESTCONTAINERS_RYUK_DISABLED=true` helps if Ryuk does not start. `camunda.process-test.runtime-mode: remote` runs against an engine that is already up. Camunda Process Test has no in-memory engine.

## Assertion helper

`CamundaAssert` takes element ids as strings. Bridge it to the generated `FlowNodes` and `ProcessPath` once, in the test sources:

```kotlin
import io.camunda.process.test.api.assertions.ProcessInstanceAssert
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.path.ProcessPath

fun ProcessInstanceAssert.hasCompletedElements(vararg elements: FlowNode): ProcessInstanceAssert =
    hasCompletedElements(*elements.map { it.id.value }.toTypedArray())

fun ProcessInstanceAssert.hasCompletedElementsInOrder(path: ProcessPath<*>): ProcessInstanceAssert =
    hasCompletedElementsInOrder(*path.ids)
```

## Test

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var processTestContext: CamundaProcessTestContext

    @Autowired
    private lateinit var process: LeasingProcessAdapter

    @MockkBean(relaxed = true)
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @MockkBean(relaxed = true)
    private lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        every { orderBikeUseCase.orderBike(any(), any()) } returns OrderId("ORDER-1")
        val id = submit()
        val instance = ProcessInstanceSelectors.byProcessId(BikeLeasingProcessProcessApi.PROCESS_ID.value)

        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(id)
        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskOrderBike)
        process.correlateHandoverReported(id)
        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        val path = ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
            .then { it.businessRuleTaskCheckCreditRating }
            .then { it.gatewayIsSolvent }
            .onto { it.subProcessConcludeContract }
            .inside {
                enter { it.startEventCustomerEligible }
                    .then { it.serviceTaskSendContract }
                    .then { it.gatewayAwaitSignature }
                    .then { it.eventContractSigned }
                    .then { it.endEventContractValid }
            }
            .then { it.gatewayFork }
            .then { it.serviceTaskIssueInsurancePolicy }
            .then { it.gatewayJoin }
            .then { it.eventHandoverReported }
            .then { it.eventWithdrawalPeriodElapsed }
            .then { it.serviceTaskActivateLeasing }
            .then { it.endEventLeasingActive }
        assertThatProcessInstance(instance).isCompleted().hasCompletedElementsInOrder(path)
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }
}
```

`submit()` builds a `LeasingApplication`, calls `process.submitRequest(application)` and returns its id. Imports: `io.camunda.process.test.api.{CamundaSpringProcessTest, CamundaProcessTestContext}`, `io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance`, `io.camunda.process.test.api.assertions.ProcessInstanceSelectors`, `com.ninjasquad.springmockk.MockkBean`, and `io.miragon.bpmn.runtime.path.{ProcessPath, then, onto, inside, enter}`.

## Driving wait states

- Start and messages: through the real adapter. A message start returns no key, so select the instance with `byProcessId`; a started instance gives `ProcessInstanceSelectors.byKey(key)`.
- Service tasks: the real workers pick up the jobs; stub the mocked use case to choose the branch, e.g. `throws BikeUnavailableException(…)` for the error boundary path.
- User tasks: `processTestContext.completeUserTask(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, mapOf(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to true))`, or the adapter method that completes it.
- Timers: `processTestContext.increaseTime(…)`, slightly beyond the timer's duration.

## Pitfalls

- The engine runs asynchronously. `CamundaAssert` polls until a timeout, so asserting the element before a wait state is how the test waits. Assert it before each `correlate…` call and before `increaseTime`; a timer that does not exist yet is not moved.
- Mock every use case that a worker of the path calls; an unmocked one runs the real service.
- A parallel gateway: walk the second branch as its own path from `FlowNodes.GatewayFork` and assert it with a second `hasCompletedElementsInOrder`.
- An element left through a boundary event is terminated, not completed: assert it with `hasTerminatedElements(FlowNodes.X.ELEMENT_ID)`.
- A wrapper bean built from the production client (e.g. `ProcessEngineApi`) must be rebound to the test client; the blueprint imports a `@TestConfiguration` for that.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.kt
