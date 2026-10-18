# Camunda 7 family, embedded · process test (Java)

Use this when the engine runs inside the Spring Boot app. The test boots the app with the real engine on H2, the real delegates and the real process adapter, and mocks the use cases. The job executor is off, so the test drives every async continuation and timer itself.

Contents: `src/test/resources/application-test.yaml` · Helpers (test sources, next to the process tests) · Test · Notes

| Engine | Import root | Assert artifact | Config prefix |
|---|---|---|---|
| Camunda 7 | `org.camunda.bpm.engine` | `org.camunda.bpm:camunda-bpm-assert` | `camunda.bpm` |
| CIB seven | `org.cibseven.bpm.engine` | `org.cibseven.bpm:cibseven-bpm-assert` | `camunda.bpm` |
| Operaton | `org.operaton.bpm.engine` | `org.operaton.bpm:operaton-bpm-assert` | `operaton.bpm` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` | `org.finos.fluxnova.bpm:fluxnova-bpm-assert` | `fluxnova.bpm` |

Test dependencies: the assert artifact, `com.h2database:h2`, `io.miragon:bpmn-to-code-runtime`. `@MockitoBean` needs Spring Framework 6.2 or later; older projects use `@MockBean`.

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

```java
public final class ProcessTestUtils {

    private ProcessTestUtils() {
    }

    public static void executeJobFor(ProcessEngine engine, FlowNode node) {
        Job job = engine.getManagementService().createJobQuery().messages().activityId(node.getId().getValue()).singleResult();
        if (job == null) throw new IllegalStateException("no async-continuation job for '" + node.getId().getValue() + "'");
        engine.getManagementService().executeJob(job.getId());
    }

    public static void fireTimer(ProcessEngine engine, FlowNode node) {
        Job timer = engine.getManagementService().createJobQuery().timers().activityId(node.getId().getValue()).singleResult();
        if (timer == null) throw new IllegalStateException("no timer job for '" + node.getId().getValue() + "'");
        engine.getManagementService().executeJob(timer.getId());
    }

    public static void continueToNextWaitState(ProcessEngine engine) {
        for (int i = 0; i < 50; i++) {
            List<Job> jobs = engine.getManagementService().createJobQuery().active().messages().listPage(0, 1);
            if (jobs.isEmpty()) return;
            engine.getManagementService().executeJob(jobs.get(0).getId());
        }
    }

    public static ProcessInstance findProcessInstance(RuntimeService runtimeService, String businessKey) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(BikeLeasingProcessApi.PROCESS_ID.getValue())
                .processInstanceBusinessKey(businessKey)
                .singleResult();
        if (instance == null) throw new IllegalStateException("no instance with business key " + businessKey);
        return instance;
    }
}
```

`FlowNode` is `io.miragon.bpmn.runtime.FlowNode`. `executeJobFor(X)` runs the job that `asyncBefore` or `asyncAfter` created on element X.

## Test

```java
@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired private BikeLeasingProcessAdapter process;
    @Autowired private RuntimeService runtimeService;
    @Autowired private ProcessEngine processEngine;

    @MockitoBean private OrderBikeUseCase orderBikeUseCase;
    @MockitoBean private ActivateLeasingUseCase activateLeasingUseCase;
    @MockitoBean private SendContractUseCase sendContractUseCase;
    @MockitoBean private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @BeforeEach
    void setUp() {
        init(processEngine);
    }

    @Test
    @DisplayName("bike unavailable - an accepted alternative is re-ordered and the leasing becomes active")
    void bikeUnavailableAlternativeAccepted() {
        when(orderBikeUseCase.orderBike(any(), any()))
                .thenThrow(new BikeUnavailableException(new BikeId("BIKE-900")))
                .thenReturn(new OrderId("ORDER-2"));
        LeasingApplication application = testLeasingApplication().id(ApplicationId.newId()).build();
        process.startBikeLeasing(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id().value().toString());

        executeJobFor(processEngine, FlowNodes.startEventLeasingRequestReceived());
        executeJobFor(processEngine, FlowNodes.serviceTaskSendContract());
        process.notifyContractSigned(application.id());
        executeJobFor(processEngine, FlowNodes.eventContractSigned());
        executeJobFor(processEngine, FlowNodes.serviceTaskIssueInsurancePolicy());
        executeJobFor(processEngine, FlowNodes.serviceTaskOrderBike());
        process.completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        continueToNextWaitState(processEngine);
        process.notifyHandoverReported(application.id());
        executeJobFor(processEngine, FlowNodes.eventHandoverReported());
        fireTimer(processEngine, FlowNodes.eventWithdrawalPeriodElapsed());

        String[] orderBranch = PathWalk.from(FlowNodes.gatewayFork())
                .then(n -> n.gatewayBikeSourceJoin())
                .then(n -> n.serviceTaskOrderBike())
                .interruptedBy(FlowNodes.serviceTaskOrderBike(), n -> n.eventBikeUnavailable())
                .then(n -> n.userTaskClarifyAlternative())
                .then(n -> n.gatewayAlternativeFound())
                .then(n -> n.gatewayBikeSourceJoin())
                .then(n -> n.serviceTaskOrderBike())
                .then(n -> n.gatewayJoin())
                .then(n -> n.eventHandoverReported())
                .then(n -> n.eventWithdrawalPeriodElapsed())
                .end(n -> n.endEventLeasingActive())
                .getIds();
        assertThat(instance).isEnded().hasPassedInOrder(orderBranch);
        verify(activateLeasingUseCase).activate(application.id());
    }
}
```

Static imports: `init`, `assertThat` from `<root>.test.assertions.bpmn.BpmnAwareTests`, plus the helpers above. `PathWalk` is `io.miragon.bpmn.runtime.path.PathWalk`.

## Notes

- **Mock every use case a delegate on the walked path calls.** An unmocked one runs the real service against H2 and usually fails into an incident. Stub the results that pick a branch or that a delegate reads: an unstubbed mock returns `null`, so `orderBike` without a stub fails the delegate with a `NullPointerException`.
- **Autowire the outbound port(s) instead of the adapter class when the test lives in the generated API's package.** Otherwise test → adapter → generated API closes a package cycle that ArchUnit's `slices().beFreeOfCycles()` rejects.
- **Use a fresh business key per test** (`ApplicationId.newId()`). The H2 engine outlives a single test, so `findProcessInstance` with a fixed key finds old instances and `singleResult()` throws.
- Wait for an element with `assertThat(instance).isWaitingAt(FlowNodes.X.ELEMENT_ID)`, and check variables with `.variables().containsEntry(...)`.
- `executeJobFor` failing with "no async-continuation job" means the token is elsewhere: a mock picked another branch, or the instance parked earlier. A delegate exception surfaces directly from `executeJob`.
- Parallel branches: walk each branch from the fork, then assert each with `hasPassedInOrder`, or unite them with `PathWalk.nodesOf(...)` for `hasPassed`.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.java
