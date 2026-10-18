# Camunda 7 family, remote · external task worker (Kotlin)

Applies when the engine runs in another process (Camunda 7, CIB seven, Operaton) and the service subscribes to external tasks through the Spring Boot external task client. The `ServiceTasks` constant is the topic.

| Family | Client and annotation | Config prefix |
|---|---|---|
| CIB seven | `org.cibseven.bpm.client.…` | `camunda.bpm.client` |
| Operaton | `org.operaton.bpm.client.…` | `operaton.bpm.client` |
| Camunda 7 | `org.camunda.bpm.client.…` | `camunda.bpm.client` |

## Base class (once per service)

```kotlin
abstract class BaseExternalTaskWorker : ExternalTaskHandler {

    protected val log = KotlinLogging.logger {}
    protected open val failureRetries: Int = 0
    protected open val failureRetryTimeoutMs: Long = 0L

    override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        try {
            executeTask(externalTask, externalTaskService)
        } catch (e: Exception) {
            log.error(e) { "Error while processing external task '${externalTask.topicName}'" }
            // The engine reports retries == null on the first failure, so grant the full budget once.
            val remainingRetries = externalTask.retries?.let { it - 1 } ?: failureRetries
            externalTaskService.handleFailure(
                externalTask,
                e.message ?: "Error while processing external task",
                e.stackTraceToString(),
                remainingRetries.coerceAtLeast(0),
                failureRetryTimeoutMs,
            )
        }
    }

    abstract fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService)
}
```

`retries = 0` raises an incident. The worker, not the model, owns the retry budget (there is no `failedJobRetryTimeCycle` for external tasks).

## Worker

```kotlin
@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) : BaseExternalTaskWorker() {

    override val failureRetries: Int = 3
    override val failureRetryTimeoutMs: Long = 10_000L

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val bikeId = externalTask.getVariable<String>(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.value)
        val orderId = try {
            useCase.orderBike(ApplicationId.of(externalTask.businessKey), BikeId(bikeId))
        } catch (e: BikeUnavailableException) {
            externalTaskService.handleBpmnError(externalTask, Errors.BIKE_UNAVAILABLE.code, e.message)
            return
        }
        externalTaskService.complete(
            externalTask,
            mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to orderId.value),
        )
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

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/kotlin-gradle/service/example-service/src/main/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorker.kt
