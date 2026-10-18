# Camunda 7 family, embedded · JavaDelegate unit test (Kotlin)

Use this for a `JavaDelegate` of Camunda 7, CIB seven, Operaton or Fluxnova. The test mocks the use case and the `DelegateExecution` with MockK and starts no Spring context and no engine.

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

## Test

```kotlin
import io.miragon.blueprint.adapter.process.BikeLeasingProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cibseven.bpm.engine.delegate.BpmnError
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OrderBikeDelegateTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = OrderBikeDelegate(useCase)

    private val variables = FlowNodes.ServiceTaskOrderBike.Variables
    private val applicationId = ApplicationId.new()
    private val bikeId = BikeId("BIKE-900")

    @BeforeEach
    fun givenTheApplicationAndItsBike() {
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { execution.getVariable(variables.BIKE_ID.value) } returns bikeId.value
        every { execution.currentActivityId } returns FlowNodes.ServiceTaskOrderBike.ELEMENT_ID
    }

    @Test
    fun `orders the bike the process carries and stores the order id`() {
        every { useCase.orderBike(applicationId, bikeId) } returns OrderId("ORDER-900")
        every { execution.setVariable(variables.ORDER_ID.value, "ORDER-900") } just Runs

        underTest.execute(execution)

        verify { useCase.orderBike(applicationId, bikeId) }
        verify { execution.setVariable(variables.ORDER_ID.value, "ORDER-900") }
        confirmVerified(useCase)
    }

    @Test
    fun `turns an unavailable bike into the bikeUnavailable BPMN error`() {
        every { useCase.orderBike(applicationId, bikeId) } throws BikeUnavailableException(bikeId)

        assertThatThrownBy { underTest.execute(execution) }
            .isInstanceOf(BpmnError::class.java)
            .extracting("errorCode")
            .isEqualTo(Errors.BIKE_UNAVAILABLE.code)
        verify(exactly = 0) { execution.setVariable(any<String>(), any()) }
        verify { useCase.orderBike(applicationId, bikeId) }
        confirmVerified(useCase)
    }

    @Test
    fun `lets a technical failure reach the engine unchanged`() {
        val failure = IllegalStateException("dealer unreachable")
        every { useCase.orderBike(applicationId, bikeId) } throws failure

        assertThatThrownBy { underTest.execute(execution) }.isSameAs(failure)
        verify(exactly = 0) { execution.setVariable(any<String>(), any()) }
        verify { useCase.orderBike(applicationId, bikeId) }
        confirmVerified(useCase)
    }
}
```

## Notes

- Call `execute`, even when the delegate extends a `BaseDelegate` and implements `executeTask`. Then the base class runs exactly as it does in the engine.
- The `currentActivityId` stub is there for a `BaseDelegate` that logs it on failure. Without the stub, the strict mock fails inside the log call and can hide the exception under test. Drop it if nothing reads it.
- `confirmVerified(useCase)` only. `DelegateExecution` gets many incidental reads, so check its writes explicitly instead, including `verify(exactly = 0)` on the error paths.
- The technical-failure case proves the delegate does not wrap the exception in a `BpmnError`. With the exception, the engine rolls back, then retries or raises an incident. That behaviour belongs to the engine and is not unit-tested here.
- A delegate that writes nothing: drop the `setVariable` stubs and the `verify(exactly = 0)` lines.

Reference: https://github.com/miragon-blueprints/fullstack-example/blob/main/service/app/src/test/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeDelegateTest.kt
