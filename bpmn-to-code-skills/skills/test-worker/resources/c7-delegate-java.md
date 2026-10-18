# Camunda 7 family, embedded · JavaDelegate unit test (Java)

Use this for a `JavaDelegate` of Camunda 7, CIB seven, Operaton or Fluxnova. The test mocks the use case and the `DelegateExecution` with Mockito and starts no Spring context and no engine.

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

## Test

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.BpmnError;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBikeDelegateTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final OrderBikeDelegate underTest = new OrderBikeDelegate(useCase);

    private final ApplicationId applicationId = ApplicationId.newId();
    private final BikeId bikeId = new BikeId("BIKE-900");

    @BeforeEach
    void givenTheApplicationAndItsBike() {
        when(execution.getProcessBusinessKey()).thenReturn(applicationId.value().toString());
        when(execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue())).thenReturn(bikeId.value());
    }

    @Test
    @DisplayName("orders the bike the process carries and stores the order id")
    void ordersTheBike() throws Exception {
        when(useCase.orderBike(applicationId, bikeId)).thenReturn(new OrderId("ORDER-900"));

        underTest.execute(execution);

        verify(useCase).orderBike(applicationId, bikeId);
        verify(execution).setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-900");
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("turns an unavailable bike into the bikeUnavailable BPMN error")
    void bikeUnavailable() {
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(new BikeUnavailableException(bikeId));

        assertThatThrownBy(() -> underTest.execute(execution))
                .isInstanceOf(BpmnError.class)
                .extracting("errorCode")
                .isEqualTo(Errors.BIKE_UNAVAILABLE.getCode());
        verify(execution, never()).setVariable(anyString(), any());
        verify(useCase).orderBike(applicationId, bikeId);
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("lets a technical failure reach the engine unchanged")
    void technicalFailure() {
        IllegalStateException failure = new IllegalStateException("dealer unreachable");
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(failure);

        assertThatThrownBy(() -> underTest.execute(execution)).isSameAs(failure);
        verify(execution, never()).setVariable(anyString(), any());
        verify(useCase).orderBike(applicationId, bikeId);
        verifyNoMoreInteractions(useCase);
    }
}
```

## Notes

- Call `execute`, even when the delegate extends a `BaseDelegate` and implements `executeTask`. Then the base class runs exactly as it does in the engine. `execute` declares `throws Exception`, so the happy-path test declares it too.
- `mock()` is lenient: an unstubbed `getCurrentActivityId()` (read by a logging `BaseDelegate`) returns `null`. The strictness comes from `verifyNoMoreInteractions`.
- Use `verifyNoMoreInteractions(useCase)` only. `DelegateExecution` gets many incidental reads, so check its writes explicitly instead, including `never()` on the error paths.
- The technical-failure case proves the delegate does not wrap the exception in a `BpmnError`. With the exception, the engine rolls back, then retries or raises an incident. That behaviour belongs to the engine and is not unit-tested here.

Reference: https://github.com/miragon-blueprints/fullstack-example/blob/main/service/app/src/test/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeDelegateTest.kt
