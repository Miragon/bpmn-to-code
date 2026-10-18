# process-engine-api · Worker test (Java)

Applies to `@ProcessEngineWorker` methods (`dev.bpm-crafters.process-engine-worker`) on any engine adapter. The method is a plain method: call it directly with the variable values and mock only the use case. No engine, no Spring context, and no `ServiceTaskCompletionApi` mock, because the library completes, errors or fails the task from what the method returns or throws.

## Test

```java
package io.miragon.blueprint.adapter.inbound.cibseven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBikeWorkerTest {

    private static final String APPLICATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String BIKE_ID = "BIKE-900";

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final OrderBikeWorker underTest = new OrderBikeWorker(useCase);

    @Test
    @DisplayName("orders the bike and returns the order id under its generated name")
    void ordersBike() throws BpmnErrorOccurred {
        when(useCase.orderBike(ApplicationId.of(APPLICATION_ID), new BikeId(BIKE_ID))).thenReturn(new OrderId("ORDER-1"));

        Map<String, Object> result = underTest.orderBike(APPLICATION_ID, BIKE_ID);

        assertThat(result).isEqualTo(Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-1"));
        verify(useCase).orderBike(ApplicationId.of(APPLICATION_ID), new BikeId(BIKE_ID));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("reports an unavailable bike as the generated BPMN error")
    void reportsBikeUnavailable() {
        when(useCase.orderBike(any(), any())).thenThrow(new BikeUnavailableException(new BikeId(BIKE_ID)));

        BpmnErrorOccurred error = assertThrows(BpmnErrorOccurred.class, () -> underTest.orderBike(APPLICATION_ID, BIKE_ID));

        assertThat(error.getErrorCode()).isEqualTo(Errors.BIKE_UNAVAILABLE.getCode());
        verify(useCase).orderBike(ApplicationId.of(APPLICATION_ID), new BikeId(BIKE_ID));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("lets a technical failure through so the library fails the task for retry")
    void propagatesTechnicalFailure() {
        IllegalStateException failure = new IllegalStateException("dealer unreachable");
        when(useCase.orderBike(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> underTest.orderBike(APPLICATION_ID, BIKE_ID)).isSameAs(failure);

        verify(useCase).orderBike(ApplicationId.of(APPLICATION_ID), new BikeId(BIKE_ID));
        verifyNoMoreInteractions(useCase);
    }
}
```

## What each case proves

| Case | Assertion | Meaning at runtime |
|---|---|---|
| happy path | returned map equals the expected map with `FlowNodes.<Node>.Variables.X.getValue()` keys | the task completes with exactly these variables |
| BPMN error | `getErrorCode()` equals `Errors.X.getCode()` | the boundary event with that code catches it; nothing else is written |
| technical failure | the original exception leaves the method unchanged | the library fails the task with `retries - 1` (or the `FailJobException` values) |

- A `void` worker has no map assertion. Keep the `verify`.
- If the worker throws `FailJobException`, assert `getRetryCount()` and `getRetryBackoff()` on it.
- Compare the whole map with `isEqualTo`, so an extra or misspelled key fails the test.
- `BpmnErrorOccurred` is checked: the happy-path test method declares `throws BpmnErrorOccurred`.
- Missing mandatory `@Variable`s and the topic subscription are library behaviour. They are covered by `/test-process`, not here.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorker.java
