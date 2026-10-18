# process-engine-api · Process adapter test (Java)

Applies to adapters that call `StartProcessApi`, `CorrelationApi`, `SignalApi` or `UserTaskCompletionApi` (`dev.bpm-crafters.process-engine-api`), whatever engine adapter runs below. Mock the APIs, capture the command with a Mockito `ArgumentCaptor` and assert every field of it. Stub the futures with `CompletableFuture.completedFuture(...)`.

## Test

```java
class BikeLeasingProcessAdapterTest {

    private final StartProcessApi startProcessApi = mock(StartProcessApi.class);
    private final CorrelationApi correlationApi = mock(CorrelationApi.class);
    private final UserTaskSupport userTaskSupport = mock(UserTaskSupport.class);
    private final UserTaskCompletionApi userTaskCompletionApi = mock(UserTaskCompletionApi.class);
    private final BikeLeasingProcessAdapter underTest =
            new BikeLeasingProcessAdapter(startProcessApi, correlationApi, userTaskSupport, userTaskCompletionApi);

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final String key = id.value().toString();

    @Test
    @DisplayName("submitRequest starts the process by message with the start variables and keys")
    void submitRequest() {
        when(startProcessApi.startProcess(any())).thenReturn(CompletableFuture.completedFuture(mock(ProcessInformation.class)));

        underTest.submitRequest(testLeasingApplication().id(id).bikeId(new BikeId("BIKE-900")).build());

        ArgumentCaptor<StartProcessByMessageCmd> cmd = ArgumentCaptor.forClass(StartProcessByMessageCmd.class);
        verify(startProcessApi).startProcess(cmd.capture());
        verifyNoMoreInteractions(startProcessApi);
        assertThat(cmd.getValue().getMessageName()).isEqualTo(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue());
        assertThat(cmd.getValue().get()).isEqualTo(Map.of(
                FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), key,
                FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), "BIKE-900",
                CommonRestrictions.BUSINESS_KEY, key,
                CommonRestrictions.CORRELATION_KEY, key));
    }

    @Test
    @DisplayName("notifyContractSigned correlates the message by the application id")
    void notifyContractSigned() {
        when(correlationApi.correlateMessage(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        underTest.notifyContractSigned(id);

        ArgumentCaptor<CorrelateMessageCmd> cmd = ArgumentCaptor.forClass(CorrelateMessageCmd.class);
        verify(correlationApi).correlateMessage(cmd.capture());
        verifyNoMoreInteractions(correlationApi);
        assertThat(cmd.getValue().getMessageName()).isEqualTo(Messages.MIRAVELO_CONTRACT_SIGNED.getValue());
        assertThat(cmd.getValue().getCorrelation().get().getCorrelationKey()).isEqualTo(key);
        assertThat(cmd.getValue().getRestrictions()).isEqualTo(Map.of("useGlobalCorrelationKey", "true"));
        assertThat(cmd.getValue().get()).isEmpty();
    }

    @Test
    @DisplayName("completeAlternativeClarification completes the task of this application")
    void completeAlternativeClarification() {
        String elementId = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID;
        TaskInformation task = new TaskInformation("task-1", Map.of(CommonRestrictions.ACTIVITY_ID, elementId));
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task));
        when(userTaskSupport.getPayload("task-1"))
                .thenReturn(Map.of(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), key));
        when(userTaskCompletionApi.completeTask(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        underTest.completeAlternativeClarification(id, true);

        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(userTaskCompletionApi).completeTask(cmd.capture());
        verifyNoMoreInteractions(userTaskCompletionApi);
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-1");
        String output = FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue();
        assertThat(cmd.getValue().get()).isEqualTo(Map.of(output, true));
    }

    @Test
    @DisplayName("a failed correlation surfaces the engine exception unwrapped")
    void correlationFailure() {
        IllegalStateException failure = new IllegalStateException("no instance waits for the message");
        when(correlationApi.correlateMessage(any())).thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> underTest.notifyContractSigned(id)).isSameAs(failure);
    }
}
```

- Imports: `dev.bpmcrafters.processengineapi.Empty` (use `Empty.INSTANCE`), `ProcessInformation` from `...processengineapi.process`, `TaskInformation`, `CompleteTaskCmd` and `UserTaskCompletionApi` from `...processengineapi.task`, `UserTaskSupport` from `...processengineapi.task.support`, `SendSignalCmd` from `...processengineapi.correlation`.
- Payload and correlation sit behind suppliers: read them with `cmd.getValue().get()` and `getCorrelation().get()`.
- Compare the whole payload with `isEqualTo(Map.of(...))` keyed by the generated names, so an extra or renamed variable fails.
- Start by definition: assert `getDefinitionKey()` equals `BikeLeasingProcessProcessApi.PROCESS_ID.getValue()`. Signal: capture `SendSignalCmd`, assert `getSignalName()` equals `Signals.X.getValue()`, `getRestrictions()` and `get()`.
- User task: add a test with an empty pool or a task of another application. The adapter must fail and `completeTask` must not be called (`verifyNoInteractions(userTaskCompletionApi)`).
- A test that stubs `failedFuture(...)` proves the adapter joins and does not swallow the error.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapterTest.java
