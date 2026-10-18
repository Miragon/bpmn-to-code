# Camunda 7 family, remote · process test (Java)

Applies when the service owns the model but the engine runs elsewhere (external tasks, engine REST API). The test runs a standalone in-memory engine without Spring and drives it by topic. Workers and the process adapter speak REST and cannot attach to it, so the test completes each external task with the output its worker returns and starts or correlates through `RuntimeService` with the same generated names; `/test-worker` and `/test-process-adapter` cover the mapping.

| Family | Engine and assertions | `MockExpressionManager`, `Mocks` | Test dependencies |
|---|---|---|---|
| CIB seven | `org.cibseven.bpm.engine…` | `org.cibseven.bpm.engine.test.mock` | `cibseven-engine`, `cibseven-bpm-assert`, `h2` |
| Operaton | `org.operaton.bpm.engine…` | `org.operaton.bpm.engine.impl.mock` | `operaton-engine`, `operaton-bpm-assert`, `h2` |
| Camunda 7 | `org.camunda.bpm.engine…` | `org.camunda.bpm.engine.test.mock` | `camunda-engine`, `camunda-bpm-assert`, `h2` |

## Helpers (copy once into the test sources)

```java
final class ProcessDriveUtils {

    private static final String TEST_WORKER = "test-worker";

    static ProcessInstance startByMessage(ProcessEngine engine, MessageName message, String businessKey, Map<String, Object> variables) {
        ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByMessage(message.getValue(), businessKey, variables);
        executeAsyncContinuations(engine);
        return instance;
    }

    static void completeExternalTask(ProcessEngine engine, String topic, Map<String, Object> variables) {
        engine.getExternalTaskService().complete(lockSingleExternalTask(engine, topic).getId(), TEST_WORKER, variables);
        executeAsyncContinuations(engine);
    }

    static void raiseBpmnErrorOnExternalTask(ProcessEngine engine, String topic, BpmnErrorDefinition error) {
        engine.getExternalTaskService().handleBpmnError(lockSingleExternalTask(engine, topic).getId(), TEST_WORKER, error.getCode());
        executeAsyncContinuations(engine);
    }

    static void correlateMessage(ProcessEngine engine, MessageName message, String businessKey) {
        engine.getRuntimeService().createMessageCorrelation(message.getValue()).processInstanceBusinessKey(businessKey).correlate();
        executeAsyncContinuations(engine);
    }

    private static ExternalTask lockSingleExternalTask(ProcessEngine engine, String topic) {
        ExternalTask task = engine.getExternalTaskService().createExternalTaskQuery().topicName(topic).notLocked().singleResult();
        Objects.requireNonNull(task, "no waiting external task for topic '" + topic + "'");
        engine.getExternalTaskService().lock(task.getId(), TEST_WORKER, 60_000L);
        return task;
    }

    private static void executeAsyncContinuations(ProcessEngine engine) {
        for (int i = 0; i < 100; i++) {
            List<Job> jobs = engine.getManagementService().createJobQuery().active().messages().listPage(0, 1);
            if (jobs.isEmpty()) return;
            engine.getManagementService().executeJob(jobs.get(0).getId());
        }
        throw new IllegalStateException("async continuations did not settle");
    }
}
```

## Test

```java
class BikeLeasingProcessTest {

    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:process-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000");
        configuration.setExpressionManager(new MockExpressionManager());
        engine = configuration.buildProcessEngine();
        Mocks.register("bikeOrderAuditListener", (ExecutionListener) execution -> { });
        Mocks.register("clarifyAlternativeTaskListener", (TaskListener) task -> { });
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("bpmn/bike-leasing.bpmn")
                .addClasspathResource("dmn/check-credit-rating.dmn")
                .deploy();
        init(engine);
    }

    @AfterEach
    void tearDown() {
        Mocks.reset();
        engine.close();
    }

    @Test
    @DisplayName("bike unavailable - order is interrupted and waits for clarification")
    void bikeUnavailableWaitsForClarification() {
        String businessKey = UUID.randomUUID().toString();
        Map<String, Object> variables = Map.of(
                StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), businessKey,
                StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), "BIKE-900",
                StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), 3500.0,
                StartEventLeasingRequestReceived.Variables.AGE.getValue(), 35);
        ProcessInstance instance = startByMessage(engine, Messages.MIRAVELO_LEASING_REQUEST_RECEIVED, businessKey, variables);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT, Map.of());
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY, Map.of());
        raiseBpmnErrorOnExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE);

        String[] ids = PathWalk.from(FlowNodes.gatewayFork())
                .then(next -> next.gatewayBikeSourceJoin())
                .then(next -> next.serviceTaskOrderBike())
                .interruptedBy(FlowNodes.serviceTaskOrderBike(), next -> next.eventBikeUnavailable())
                .getIds();
        assertThat(instance).isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID).hasPassedInOrder(ids);
    }
}
```

- Register every bean the model references by expression (`delegateExpression`, listeners, `${bean.method()}`) with `Mocks.register`; external tasks need none. Deploy called processes and DMN tables the scenario reaches.
- Every helper settles async continuations, so the next external task exists when the next line runs. Timers stay untouched: fire one with `getManagementService().createJobQuery().timers().activityId(FlowNodes.X.ELEMENT_ID)` and `executeJob`. A none start event starts with `startProcessInstanceByKey(<Process>ProcessApi.PROCESS_ID.getValue(), …)`.
- `hasPassedInOrder` (static import from `BpmnAwareTests`, like `assertThat` and `init`) only sees finished activities: end the path before the wait state and assert that with `isWaitingAt`.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/java-maven/service/example-service/src/test/java/io/miragon/blueprint/adapter/process/BikeLeasingProcessTest.java
