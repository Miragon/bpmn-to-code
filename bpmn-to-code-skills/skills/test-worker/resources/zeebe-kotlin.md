# Zeebe · Job worker unit test (Kotlin)

Applies to a `@JobWorker` of the Camunda Spring SDK. Call the handler method directly with MockK mocks; no engine and no Spring context.

Contents: Manual variant (`autoComplete = [false]`) · Auto-complete variant · Pitfalls

## Manual variant (`autoComplete = [false]`)

Mock `JobClient` and `ActivatedJob` relaxed and verify the whole fluent command chain, up to `send()`.

```kotlin
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val client = mockk<JobClient>(relaxed = true)
    private val job = mockk<ActivatedJob>(relaxed = true)
    private val underTest = OrderBikeWorker(useCase)

    private val applicationId = ApplicationId.new()
    private val bikeId = BikeId("BIKE-900")

    @Test
    fun `completes the job with the order id`() {
        every { useCase.orderBike(applicationId, bikeId) } returns OrderId("ORDER-1")

        underTest.handle(client, job, applicationId.value.toString(), bikeId.value)

        verify {
            useCase.orderBike(applicationId, bikeId)
            client.newCompleteCommand(job)
                .variables(mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-1"))
                .send()
        }
        confirmVerified(useCase, client)
    }

    @Test
    fun `throws the bike unavailable BPMN error`() {
        val unavailable = BikeUnavailableException(bikeId)
        every { useCase.orderBike(applicationId, bikeId) } throws unavailable

        underTest.handle(client, job, applicationId.value.toString(), bikeId.value)

        verify {
            useCase.orderBike(applicationId, bikeId)
            client.newThrowErrorCommand(job)
                .errorCode(Errors.BIKE_UNAVAILABLE.code)
                .errorMessage(unavailable.message)
                .send()
        }
        confirmVerified(useCase, client)
    }

    @Test
    fun `fails the job with one retry less on a technical error`() {
        every { useCase.orderBike(applicationId, bikeId) } throws IllegalStateException("dealer unreachable")
        every { job.retries } returns 3

        underTest.handle(client, job, applicationId.value.toString(), bikeId.value)

        verify {
            useCase.orderBike(applicationId, bikeId)
            client.newFailCommand(job)
                .retries(2)
                .retryBackoff(Duration.ofSeconds(10))
                .errorMessage("dealer unreachable")
                .send()
        }
        confirmVerified(useCase, client)
    }
}
```

`confirmVerified(client)` covers "nothing else was sent": a complete command in the error test would fail it.

## Auto-complete variant

Assert the returned map. For the failure case, assert that the exception propagates (`assertThrows<IllegalStateException> { underTest.handle("ORDER-1") }`): the SDK turns it into the fail command, a `CamundaError.bpmnError(…)` into the throw-error command.

```kotlin
@Test
fun `returns whether the cancellation is possible`() {
    every { useCase.requestCancellation(OrderId("ORDER-1")) } returns true

    val result = underTest.handle("ORDER-1")

    val output = FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE
    assertThat(result).containsExactlyEntriesOf(mapOf(output.value to true))
    verify { useCase.requestCancellation(OrderId("ORDER-1")) }
    confirmVerified(useCase)
}
```

## Pitfalls

- A relaxed MockK chain is keyed by the arguments of every step. Use concrete values in each step of a chain `verify`: an `any()` in the middle verifies a different child mock and fails with "send() was not called".
- Do not stub `send().join()`: relaxed mocks return a relaxed future.
- Expected keys and codes come from the generated API (`FlowNodes.<Node>.Variables.X.value`, `Errors.X.code`), not string literals.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/inbound/zeebe/OrderBikeWorkerTest.kt
