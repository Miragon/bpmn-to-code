# process-engine-api · Process test (Java)

Applies to process-engine-api projects on an embedded C7-family adapter (Camunda 7, CIB seven, Operaton). `@SpringBootTest` boots the real engine, the adapter and the real `@ProcessEngineWorker` beans. The use cases are mocks, and the bpm-assert library of the engine checks the walked path. On Camunda 8 the engine is not embedded: use Camunda Process Test (`@CamundaSpringProcessTest`) and its assertions as in `zeebe-java.md`. The workers, mocks and `PathWalk` stay the same.

## Test profile

```yaml
# src/test/resources/application-test.yaml
camunda.bpm.job-execution.enabled: false
dev.bpm-crafters.process-api.adapter.cib-seven-embedded:
  service-tasks: { delivery-strategy: embedded_scheduled, schedule-delivery-fixed-rate-in-seconds: 1 }
  user-tasks: { delivery-strategy: embedded_scheduled, schedule-delivery-fixed-rate-in-seconds: 1 }
```

The job executor is off, so the test fires async continuations and timers itself. The workers poll for external tasks on the adapter's schedule. The adapter block is engine-specific: copy its keys from the main `application.yaml` (Operaton uses `operaton.bpm`). Test dependency: the engine's bpm-assert (`org.cibseven.bpm:cibseven-bpm-assert`) and H2.

## Wait helper

```java
public static void continueToNextWaitState(ProcessEngine engine) throws InterruptedException {
    ManagementService managementService = engine.getManagementService();
    long deadline = System.currentTimeMillis() + 15_000;
    int idleIterations = 0;
    while (System.currentTimeMillis() < deadline) {
        List<Job> jobs = managementService.createJobQuery().active().messages().listPage(0, 1);
        if (!jobs.isEmpty()) {
            managementService.executeJob(jobs.get(0).getId());
            idleIterations = 0;
            continue;
        }
        long openExternalTasks = engine.getExternalTaskService().createExternalTaskQuery().count();
        idleIterations = openExternalTasks == 0 ? idleIterations + 1 : 0;
        if (idleIterations >= 3) {
            return;
        }
        Thread.sleep(200);
    }
}
```

It runs the parked async jobs and waits until the polling workers have drained every external task. Fire a timer through `managementService.createJobQuery().timers().activityId(FlowNodes.X.ELEMENT_ID).singleResult()` and `executeJob`.

## Test

```java
@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired private LeasingProcess process;
    @Autowired private RuntimeService runtimeService;
    @Autowired private ProcessEngine processEngine;

    @MockitoBean private SendContractUseCase sendContractUseCase;
    @MockitoBean private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;
    @MockitoBean private OrderBikeUseCase orderBikeUseCase;

    @BeforeEach
    void setUp() {
        init(processEngine);
        when(orderBikeUseCase.orderBike(any(), any())).thenReturn(new OrderId("ORDER-1"));
    }

    @Test
    @DisplayName("contract signed: the bike is ordered and the process waits for the handover")
    void contractSigned() throws InterruptedException {
        LeasingApplication application = testLeasingApplication().id(ApplicationId.newId()).build();
        process.submitRequest(application);
        continueToNextWaitState(processEngine);

        process.correlateContractSigned(application.id());
        continueToNextWaitState(processEngine);

        String[] orderBranch = PathWalk.from(FlowNodes.gatewayFork())
                .then(next -> next.gatewayBikeSourceJoin())
                .then(next -> next.serviceTaskOrderBike())
                .then(next -> next.gatewayJoin())
                .getIds();
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.getValue())
                .processInstanceBusinessKey(application.id().value().toString())
                .singleResult();
        assertThat(instance)
                .isWaitingAt(FlowNodes.EventHandoverReported.ELEMENT_ID)
                .hasPassedInOrder(orderBranch);
        verify(orderBikeUseCase).orderBike(application.id(), application.bikeId());
    }
}
```

- Imports: `org.springframework.test.context.bean.override.mockito.MockitoBean`, `io.miragon.bpmn.runtime.path.PathWalk`, and static `assertThat` / `init` from `org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests` (Camunda 7: `org.camunda.bpm…`, Operaton: `org.operaton.bpm…`).
- Drive the instance only through the outbound port (`process.submitRequest`, `process.correlate…`) and the real workers. Call `continueToNextWaitState` after every step.
- Mock every use case a worker calls. Stub results that decide a gateway, and stub with `thenThrow` for an error boundary event.
- Use a fresh business key per test (`ApplicationId.newId()`): all tests share one engine.
- A walk that reaches an end event finishes with `.end(next -> next.endEventX()).getIds()`. For an unordered set (parallel branches, compensation handlers) use `hasPassed(walk.getDistinctIds())`.
- Delivery is at least once. With compensation or parallel work a use case may run twice, so verify with `atLeastOnce()` there.
- `dev.bpm-crafters.process-engine-api-testing` (2026.10.1) offers engine-agnostic JGiven scenarios (`ProcessScenarioTest`, `processHasPassedInOrder(ids)`). The blueprint does not use it.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.java
