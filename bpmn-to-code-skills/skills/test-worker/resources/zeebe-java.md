# Zeebe · Job worker unit test (Java)

Applies to a `@JobWorker` of the Camunda Spring SDK. Call the handler method directly with Mockito mocks; no engine and no Spring context.

Contents: Manual variant (`autoComplete = false`) · Auto-complete variant · Pitfalls

## Manual variant (`autoComplete = false`)

Mockito has no chain verification, so mock one object per step of each fluent command and verify each step on its own.

```java
class OrderBikeWorkerTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final JobClient client = mock(JobClient.class);
    private final ActivatedJob job = mock(ActivatedJob.class);
    private final OrderBikeWorker underTest = new OrderBikeWorker(useCase);

    private final CompleteJobCommandStep1 completeCommand = mock(CompleteJobCommandStep1.class);
    private final CamundaFuture<CompleteJobResponse> completeFuture = mock();
    private final ThrowErrorCommandStep1 throwErrorCommand = mock(ThrowErrorCommandStep1.class);
    private final ThrowErrorCommandStep2 throwErrorCommandStep2 = mock(ThrowErrorCommandStep2.class);
    private final CamundaFuture<ThrowErrorResponse> throwErrorFuture = mock();
    private final FailJobCommandStep1 failCommand = mock(FailJobCommandStep1.class);
    private final FailJobCommandStep2 failCommandStep2 = mock(FailJobCommandStep2.class);
    private final CamundaFuture<FailJobResponse> failFuture = mock();

    private final ApplicationId applicationId = ApplicationId.newId();
    private final BikeId bikeId = new BikeId("BIKE-900");

    @BeforeEach
    void stubCommandChains() {
        when(client.newCompleteCommand(job)).thenReturn(completeCommand);
        when(completeCommand.variables(anyMap())).thenReturn(completeCommand);
        when(completeCommand.send()).thenReturn(completeFuture);
        when(client.newThrowErrorCommand(job)).thenReturn(throwErrorCommand);
        when(throwErrorCommand.errorCode(any())).thenReturn(throwErrorCommandStep2);
        when(throwErrorCommandStep2.errorMessage(any())).thenReturn(throwErrorCommandStep2);
        when(throwErrorCommandStep2.send()).thenReturn(throwErrorFuture);
        when(client.newFailCommand(job)).thenReturn(failCommand);
        when(failCommand.retries(anyInt())).thenReturn(failCommandStep2);
        when(failCommandStep2.retryBackoff(any())).thenReturn(failCommandStep2);
        when(failCommandStep2.errorMessage(any())).thenReturn(failCommandStep2);
        when(failCommandStep2.send()).thenReturn(failFuture);
    }

    @Test
    @DisplayName("completes the job with the order id")
    void completesTheJobWithTheOrderId() {
        when(useCase.orderBike(applicationId, bikeId)).thenReturn(new OrderId("ORDER-1"));

        underTest.handle(client, job, applicationId.value().toString(), bikeId.value());

        verify(useCase).orderBike(applicationId, bikeId);
        verify(client).newCompleteCommand(job);
        verify(completeCommand).variables(Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-1"));
        verify(completeFuture).join();
        verifyNoMoreInteractions(useCase, client);
    }

    @Test
    @DisplayName("throws the bike unavailable BPMN error")
    void throwsTheBikeUnavailableBpmnError() {
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(new BikeUnavailableException(bikeId));

        underTest.handle(client, job, applicationId.value().toString(), bikeId.value());

        verify(useCase).orderBike(applicationId, bikeId);
        verify(client).newThrowErrorCommand(job);
        verify(throwErrorCommand).errorCode(Errors.BIKE_UNAVAILABLE.getCode());
        verify(throwErrorFuture).join();
        verifyNoMoreInteractions(useCase, client);
    }

    @Test
    @DisplayName("fails the job with one retry less on a technical error")
    void failsTheJobWithOneRetryLessOnATechnicalError() {
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(new IllegalStateException("dealer unreachable"));
        when(job.getRetries()).thenReturn(3);

        underTest.handle(client, job, applicationId.value().toString(), bikeId.value());

        verify(useCase).orderBike(applicationId, bikeId);
        verify(client).newFailCommand(job);
        verify(failCommand).retries(2);
        verify(failCommandStep2).retryBackoff(Duration.ofSeconds(10));
        verify(failCommandStep2).errorMessage("dealer unreachable");
        verify(failFuture).join();
        verifyNoMoreInteractions(useCase, client);
    }
}
```

Non-obvious imports: the step types are nested, e.g. `io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2`; futures are `io.camunda.client.api.CamundaFuture`, responses `io.camunda.client.api.response.*Response`. `verifyNoMoreInteractions(client)` covers "nothing else was sent": a complete command in the error test would fail it.

## Auto-complete variant

No client mocks: call `underTest.handle("ORDER-1")`, assert the returned map with `containsExactlyEntriesOf(Map.of(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), true))`, then `verify(useCase)…` and `verifyNoMoreInteractions(useCase)`. For the failure case, assert that the exception propagates (`assertThatThrownBy`): the SDK turns it into the fail command, a `CamundaError.bpmnError(…)` into the throw-error command.

## Pitfalls

- Plain `mock()` without `MockitoExtension` keeps the shared `@BeforeEach` stubs lenient; under strict stubs, stub only the chain each test uses.
- Expected keys and codes come from the generated API, not string literals.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/inbound/zeebe/OrderBikeWorkerTest.java
