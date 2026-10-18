# process-engine-api · Worker test (Kotlin)

Applies to `@ProcessEngineWorker` methods (`dev.bpm-crafters.process-engine-worker`) on any engine adapter. The method is a plain function: call it directly with the variable values and mock only the use case. No engine, no Spring context, and no `ServiceTaskCompletionApi` mock, because the library completes, errors or fails the task from what the method returns or throws.

## Test

```kotlin
package io.miragon.blueprint.adapter.inbound.cibseven

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val underTest = OrderBikeWorker(useCase)

    private val applicationId = "123e4567-e89b-12d3-a456-426614174000"
    private val bikeId = "BIKE-900"

    @Test
    fun `orders the bike and returns the order id under its generated name`() {
        every { useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId)) } returns OrderId("ORDER-1")

        val result = underTest.orderBike(applicationId, bikeId)

        val expected = mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-1")
        assertThat(result).isEqualTo(expected)
        verify { useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId)) }
        confirmVerified(useCase)
    }

    @Test
    fun `reports an unavailable bike as the generated BPMN error`() {
        every { useCase.orderBike(any(), any()) } throws BikeUnavailableException(BikeId(bikeId))

        val error = assertThrows<BpmnErrorOccurred> { underTest.orderBike(applicationId, bikeId) }

        assertThat(error.errorCode).isEqualTo(Errors.BIKE_UNAVAILABLE.code)
        verify { useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId)) }
        confirmVerified(useCase)
    }

    @Test
    fun `lets a technical failure through so the library fails the task for retry`() {
        val failure = IllegalStateException("dealer unreachable")
        every { useCase.orderBike(any(), any()) } throws failure

        assertThatThrownBy { underTest.orderBike(applicationId, bikeId) }.isSameAs(failure)

        verify { useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId)) }
        confirmVerified(useCase)
    }
}
```

## What each case proves

| Case | Assertion | Meaning at runtime |
|---|---|---|
| happy path | returned map equals the expected map with `FlowNodes.<Node>.Variables.X.value` keys | the task completes with exactly these variables |
| BPMN error | `BpmnErrorOccurred.errorCode == Errors.X.code` | the boundary event with that code catches it; nothing else is written |
| technical failure | the original exception leaves the method unchanged | the library fails the task with `retries - 1` (or the `FailJobException` values) |

- A worker without outputs returns `Unit`: drop the map assertion and keep the `verify`.
- If the worker throws `FailJobException`, assert `retryCount` and `retryBackoff` on it.
- Compare the whole map with `isEqualTo`, so an extra or misspelled key fails the test.
- Missing mandatory `@Variable`s and the topic subscription are library behaviour. They are covered by `/test-process`, not here.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorker.kt
