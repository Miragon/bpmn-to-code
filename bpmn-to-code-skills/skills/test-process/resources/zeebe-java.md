# Zeebe · Process test (Java)

Applies to Camunda 8 projects with `io.camunda:camunda-process-test-spring` (Camunda Process Test). The Spring context runs with the real workers and the real process adapter against a test engine; the use cases are `@MockitoBean`s.

## Runtime

The default runtime mode `managed` starts Camunda in a container through Testcontainers, so the test needs Docker or Podman; the blueprint uses it. For Podman, point `DOCKER_HOST` at the Podman socket (`podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}'`); `TESTCONTAINERS_RYUK_DISABLED=true` helps if Ryuk does not start. `camunda.process-test.runtime-mode: remote` runs against an engine that is already up. Camunda Process Test has no in-memory engine.

## Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
class BikeLeasingProcessTest {

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @Autowired
    private LeasingProcessAdapter process;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private ActivateLeasingUseCase activateLeasingUseCase;

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        when(orderBikeUseCase.orderBike(any(), any())).thenReturn(new OrderId("ORDER-1"));
        ApplicationId id = submit();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byProcessId(BikeLeasingProcessProcessApi.PROCESS_ID.getValue());

        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        process.correlateContractSigned(id);
        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);
        process.correlateHandoverReported(id);
        assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        String[] path = PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent())
            .onto(n -> n.subProcessConcludeContract())
            .inside(FlowNodes.subProcessConcludeContract(), s -> PathWalk.from(s.startEventCustomerEligible())
                .then(n -> n.serviceTaskSendContract())
                .then(n -> n.gatewayAwaitSignature())
                .then(n -> n.eventContractSigned())
                .end(n -> n.endEventContractValid()))
            .then(n -> n.gatewayFork())
            .then(n -> n.serviceTaskIssueInsurancePolicy())
            .then(n -> n.gatewayJoin())
            .then(n -> n.eventHandoverReported())
            .then(n -> n.eventWithdrawalPeriodElapsed())
            .then(n -> n.serviceTaskActivateLeasing())
            .end(n -> n.endEventLeasingActive())
            .getIds();
        assertThatProcessInstance(instance).isCompleted().hasCompletedElementsInOrder(path);
        verify(activateLeasingUseCase).activate(id);
    }
}
```

`submit()` builds a `LeasingApplication`, calls `process.submitRequest(application)` and returns its id. Imports: `io.camunda.process.test.api.{CamundaSpringProcessTest, CamundaProcessTestContext}`, static `io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance`, `io.camunda.process.test.api.assertions.{ProcessInstanceSelector, ProcessInstanceSelectors}`, `io.miragon.bpmn.runtime.path.PathWalk`, `org.springframework.test.context.bean.override.mockito.MockitoBean`.

`CamundaAssert` takes element ids as strings, so `PathWalk…getIds()` and `FlowNodes.X.ELEMENT_ID` plug in directly; no helper is needed.

## Driving wait states

- Start and messages: through the real adapter. A message start returns no key, so select the instance with `byProcessId`; a started instance gives `ProcessInstanceSelectors.byKey(key)`.
- Service tasks: the real workers pick up the jobs; stub the mocked use case to choose the branch, e.g. `thenThrow(new BikeUnavailableException(…))` for the error boundary path.
- User tasks: `processTestContext.completeUserTask(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, Map.of(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), true))`, or the adapter method that completes it.
- Timers: `processTestContext.increaseTime(…)`, slightly beyond the timer's duration.

## Pitfalls

- The engine runs asynchronously. `CamundaAssert` polls until a timeout, so asserting the element before a wait state is how the test waits. Assert it before each `correlate…` call and before `increaseTime`; a timer that does not exist yet is not moved.
- Mock every use case that a worker of the path calls; an unmocked one runs the real service.
- A parallel gateway: walk the second branch as its own `PathWalk.from(FlowNodes.gatewayFork())` and assert it with a second `hasCompletedElementsInOrder`.
- An element left through a boundary event is terminated, not completed: assert it with `hasTerminatedElements(FlowNodes.X.ELEMENT_ID)`.
- A path that ends before an end event has no `end(…)`: call `getIds()` on the `PathWalk` directly.
- A wrapper bean built from the production client (e.g. `ProcessEngineApi`) must be rebound to the test client; the blueprint imports a `@TestConfiguration` for that.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.java
