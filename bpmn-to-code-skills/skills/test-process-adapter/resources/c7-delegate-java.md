# Camunda 7 family, embedded · process adapter unit test (Java)

Use this for an adapter that calls `RuntimeService` / `TaskService` of Camunda 7, CIB seven, Operaton or Fluxnova. The test mocks the services and their fluent builders with Mockito and starts no engine.

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

Engine types used: `RuntimeService`, `TaskService`, `runtime.MessageCorrelationBuilder`, `runtime.SignalEventReceivedBuilder`, `task.TaskQuery`, `task.Task`, `MismatchingMessageCorrelationException`.

## Test

```java
class BikeLeasingProcessAdapterTest {

    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final BikeLeasingProcessAdapter underTest = new BikeLeasingProcessAdapter(runtimeService, taskService);

    private final ApplicationId applicationId = ApplicationId.newId();
    private final String businessKey = applicationId.value().toString();

    @Test
    @DisplayName("startBikeLeasing starts the process by message with the application id as business key")
    void startBikeLeasing() {
        LeasingApplication application = testLeasingApplication().id(applicationId).build();

        underTest.startBikeLeasing(application);

        Map<String, Object> expectedVariables = Map.of(
                FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), businessKey,
                FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), application.bikeId().value(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), application.monthlyNetIncome(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), application.age());
        verify(runtimeService).startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(), businessKey, expectedVariables);
        verifyNoMoreInteractions(runtimeService);
    }

    @Test
    @DisplayName("notifyContractSigned correlates the message by business key")
    void notifyContractSigned() {
        MessageCorrelationBuilder correlation = givenCorrelation();

        underTest.notifyContractSigned(applicationId);

        verify(runtimeService).createMessageCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.getValue());
        verify(correlation).processInstanceBusinessKey(businessKey);
        verify(correlation).correlate();
        verifyNoMoreInteractions(runtimeService, correlation);
    }

    @Test
    @DisplayName("notifyContractSigned lets a failed correlation reach the caller")
    void notifyContractSignedFails() {
        MessageCorrelationBuilder correlation = givenCorrelation();
        doThrow(new MismatchingMessageCorrelationException("no waiting instance")).when(correlation).correlate();

        assertThatThrownBy(() -> underTest.notifyContractSigned(applicationId))
                .isInstanceOf(MismatchingMessageCorrelationException.class);
    }

    @Test
    @DisplayName("completeAlternativeClarification completes the open task of the application")
    void completeAlternativeClarification() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        TaskQuery query = givenTaskQuery(task);

        underTest.completeAlternativeClarification(applicationId, true, new BikeId("BIKE-42"));

        verify(query).processInstanceBusinessKey(businessKey);
        verify(query).taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        verify(taskService).createTaskQuery();
        verify(taskService).complete("task-1", Map.of(
                FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), true,
                FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.getValue(), "BIKE-42"));
        verifyNoMoreInteractions(taskService);
    }

    @Test
    @DisplayName("completeAlternativeClarification fails when no task is open")
    void noOpenTask() {
        givenTaskQuery(null);

        assertThatThrownBy(() -> underTest.completeAlternativeClarification(applicationId, true, null))
                .isInstanceOf(IllegalStateException.class);
        verify(taskService, never()).complete(any(), any());
    }

    private MessageCorrelationBuilder givenCorrelation() {
        MessageCorrelationBuilder correlation = mock(MessageCorrelationBuilder.class);
        when(runtimeService.createMessageCorrelation(any())).thenReturn(correlation);
        when(correlation.processInstanceBusinessKey(any())).thenReturn(correlation);
        return correlation;
    }

    private TaskQuery givenTaskQuery(Task result) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceBusinessKey(any())).thenReturn(query);
        when(query.taskDefinitionKey(any())).thenReturn(query);
        when(query.singleResult()).thenReturn(result);
        return query;
    }
}
```

## Notes

- Compare whole variable maps with `Map.of(...)` (map `equals`), not with `argThat(vars -> vars.get("age").equals(35))`. Then a missing or extra variable fails the test.
- `correlate()`, `send()` and `complete(...)` return `void`. A mock does nothing by default; make it fail with `doThrow(...).when(mock).correlate()`.
- For a signal, mock `SignalEventReceivedBuilder`, let `createSignalEvent(any())` and `setVariables(any())` return it, then verify `createSignalEvent(Signals.X.getValue())`, `setVariables(expectedMap)` and `send()`.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/java-maven/service/app/src/test/java/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapterTest.java
