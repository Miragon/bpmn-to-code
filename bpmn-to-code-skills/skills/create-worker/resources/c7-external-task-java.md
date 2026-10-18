# Camunda 7 family, remote · external task worker (Java)

Applies when the engine runs in another process (Camunda 7, CIB seven, Operaton) and the service subscribes to external tasks through the Spring Boot external task client. The `ServiceTasks` constant is the topic.

Contents: Base class (once per service) · Worker · Subscription config · Pitfalls

| Family | Client and annotation | Config prefix |
|---|---|---|
| CIB seven | `org.cibseven.bpm.client.…` | `camunda.bpm.client` |
| Operaton | `org.operaton.bpm.client.…` | `operaton.bpm.client` |
| Camunda 7 | `org.camunda.bpm.client.…` | `camunda.bpm.client` |

## Base class (once per service)

```java
public abstract class BaseExternalTaskWorker implements ExternalTaskHandler {

    protected static final Logger log = LoggerFactory.getLogger(BaseExternalTaskWorker.class);

    protected int failureRetries() { return 0; }

    protected long failureRetryTimeoutMs() { return 0L; }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            executeTask(externalTask, externalTaskService);
        } catch (Exception e) {
            log.error("Error while processing external task '{}'", externalTask.getTopicName(), e);
            // The engine reports retries == null on the first failure, so grant the full budget once.
            Integer retries = externalTask.getRetries();
            int remainingRetries = retries != null ? retries - 1 : failureRetries();
            StringWriter stackTrace = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTrace));
            externalTaskService.handleFailure(externalTask,
                    e.getMessage() != null ? e.getMessage() : "Error while processing external task",
                    stackTrace.toString(), Math.max(remainingRetries, 0), failureRetryTimeoutMs());
        }
    }

    public abstract void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService);
}
```

`retries = 0` raises an incident. The worker, not the model, owns the retry budget (there is no `failedJobRetryTimeCycle` for external tasks).

## Worker

```java
@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
public class OrderBikeWorker extends BaseExternalTaskWorker {

    private final OrderBikeUseCase useCase;

    public OrderBikeWorker(OrderBikeUseCase useCase) { this.useCase = useCase; }

    @Override protected int failureRetries() { return 3; }

    @Override protected long failureRetryTimeoutMs() { return 10_000L; }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        String bikeId = externalTask.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue());
        OrderId orderId;
        try {
            orderId = useCase.orderBike(ApplicationId.of(externalTask.getBusinessKey()), new BikeId(bikeId));
        } catch (BikeUnavailableException e) {
            externalTaskService.handleBpmnError(externalTask, Errors.BIKE_UNAVAILABLE.getCode(), e.getMessage());
            return;
        }
        externalTaskService.complete(externalTask,
                Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), orderId.value()));
    }
}
```

- Package `adapter.inbound.<engine>` (`cibseven`, `operaton`, `camunda`), class `<Element>Worker`.
- The business key is the domain id. A task without output variables ends with `complete(externalTask)`.
- Only the business exception caught by an error boundary event becomes `handleBpmnError`. Everything else propagates to the base class.

## Subscription config

```yaml
camunda:
  bpm:
    client:
      base-url: http://localhost:8081/engine-rest
      worker-id: bikeLeasing-worker
      lock-duration: 20000
      async-response-timeout: 1000
      disable-backoff-strategy: true
```

Override per topic with `@ExternalTaskSubscription(topicName = …, lockDuration = 60_000)` when one task needs longer than the global lock. The lock must outlast the slowest call, or the engine hands the task to another worker meanwhile.

## Pitfalls

- Call exactly one of `complete`, `handleBpmnError`, `handleFailure` per execution. A second call fails because the lock is gone.
- Delivery is at least once: a lock that expires, or a `complete` that fails on the network after the use case ran, repeats the task. Make the use case idempotent per business key.
- `getVariable` only sees variables the task fetches. Do not restrict `variableNames` on the subscription unless every needed variable is listed.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/java-maven/service/example-service/src/main/java/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorker.java
