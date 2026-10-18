# Camunda 7 family, remote · external task worker test (Kotlin)

Applies to workers that implement `ExternalTaskHandler` (usually via a `BaseExternalTaskWorker`) and are subscribed with `@ExternalTaskSubscription`. Call `execute` directly with mocked client objects; no engine runs.

| Family | `ExternalTask`, `ExternalTaskService` |
|---|---|
| CIB seven | `org.cibseven.bpm.client.task` |
| Operaton | `org.operaton.bpm.client.task` |
| Camunda 7 | `org.camunda.bpm.client.task` |

## Test

```kotlin
class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val underTest = OrderBikeWorker(useCase)

    private val applicationId = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val bikeId = BikeId("BIKE-900")
    private val task = mockk<ExternalTask>(relaxed = true) {
        every { businessKey } returns applicationId.value.toString()
        every { getVariable<String>(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.value) } returns bikeId.value
        every { retries } returns null
    }
    private val service = mockk<ExternalTaskService>(relaxed = true)

    @Test
    fun `completes with the order id as output variable`() {
        every { useCase.orderBike(applicationId, bikeId) } returns OrderId("ORDER-1")

        underTest.execute(task, service)

        verify { useCase.orderBike(applicationId, bikeId) }
        verify { service.complete(task, mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-1")) }
        confirmVerified(useCase, service)
    }

    @Test
    fun `raises the bikeUnavailable BPMN error when the dealer cannot deliver`() {
        val error = BikeUnavailableException(bikeId)
        every { useCase.orderBike(applicationId, bikeId) } throws error

        underTest.execute(task, service)

        verify { useCase.orderBike(applicationId, bikeId) }
        verify { service.handleBpmnError(task, Errors.BIKE_UNAVAILABLE.code, error.message) }
        confirmVerified(useCase, service)
    }

    @Test
    fun `reports a technical failure with the full retry budget`() {
        every { useCase.orderBike(applicationId, bikeId) } throws IllegalStateException("dealer down")

        underTest.execute(task, service)

        verify { useCase.orderBike(applicationId, bikeId) }
        verify { service.handleFailure(task, "dealer down", any(), 3, 10_000L) }
        confirmVerified(useCase, service)
    }
}
```

## Notes

- Stub `retries` explicitly: a relaxed mock returns `0`, not `null`, so the base class would treat the call as the last retry and report `0`. Add a second failure test with `every { retries } returns 2` to assert the countdown (`1`).
- `confirmVerified` covers `useCase` and `service` only. The relaxed `task` records every getter the worker reads.
- `handleFailure` has overloads (task id or `ExternalTask`). Pass `task` so MockK picks the `ExternalTask` one.
- The topic itself is not covered here. A model change that renames it breaks compilation of the `@ExternalTaskSubscription`.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/kotlin-gradle/service/example-service/src/test/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorkerTest.kt
