# Camunda 7 family, remote · external task worker test (Java)

Applies to workers that implement `ExternalTaskHandler` (usually via a `BaseExternalTaskWorker`) and are subscribed with `@ExternalTaskSubscription`. Call `execute` directly with mocked client objects; no engine runs.

| Family | `ExternalTask`, `ExternalTaskService` |
|---|---|
| CIB seven | `org.cibseven.bpm.client.task` |
| Operaton | `org.operaton.bpm.client.task` |
| Camunda 7 | `org.camunda.bpm.client.task` |

## Test

```java
class OrderBikeWorkerTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final OrderBikeWorker underTest = new OrderBikeWorker(useCase);

    private final ApplicationId applicationId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final BikeId bikeId = new BikeId("BIKE-900");
    private final ExternalTask task = mock(ExternalTask.class);
    private final ExternalTaskService service = mock(ExternalTaskService.class);

    @BeforeEach
    void stubTask() {
        when(task.getBusinessKey()).thenReturn(applicationId.value().toString());
        when(task.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue())).thenReturn(bikeId.value());
        when(task.getRetries()).thenReturn(null);
    }

    @Test
    @DisplayName("completes with the order id as output variable")
    void completesWithTheOrderId() {
        when(useCase.orderBike(applicationId, bikeId)).thenReturn(new OrderId("ORDER-1"));

        underTest.execute(task, service);

        verify(useCase).orderBike(applicationId, bikeId);
        verify(service).complete(task, Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-1"));
        verifyNoMoreInteractions(useCase, service);
    }

    @Test
    @DisplayName("raises the bikeUnavailable BPMN error when the dealer cannot deliver")
    void raisesBikeUnavailable() {
        BikeUnavailableException error = new BikeUnavailableException(bikeId);
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(error);

        underTest.execute(task, service);

        verify(useCase).orderBike(applicationId, bikeId);
        verify(service).handleBpmnError(task, Errors.BIKE_UNAVAILABLE.getCode(), error.getMessage());
        verifyNoMoreInteractions(useCase, service);
    }

    @Test
    @DisplayName("reports a technical failure with the full retry budget")
    void reportsTechnicalFailure() {
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(new IllegalStateException("dealer down"));

        underTest.execute(task, service);

        verify(useCase).orderBike(applicationId, bikeId);
        verify(service).handleFailure(eq(task), eq("dealer down"), anyString(), eq(3), eq(10_000L));
        verifyNoMoreInteractions(useCase, service);
    }
}
```

## Notes

- Stub `getRetries()` explicitly: Mockito returns `0` for an unstubbed `Integer`, not `null`, so the base class would treat the call as the last retry and report `0`. Add a second failure test with `thenReturn(2)` to assert the countdown (`1`).
- `verifyNoMoreInteractions` covers `useCase` and `service` only. `task` is read through getters the worker is free to call.
- Do not use `MockitoExtension` strict stubs with the shared `@BeforeEach`: tests that never read a stub would fail with `UnnecessaryStubbingException`.
- The topic itself is not covered here. A model change that renames it breaks compilation of the `@ExternalTaskSubscription`.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/java-maven/service/example-service/src/test/java/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorkerTest.java
