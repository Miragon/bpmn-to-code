# Zeebe · Process adapter unit test (Java)

Applies to an adapter that calls `CamundaClient` (or a project wrapper around it). Mockito has no chain verification: mock one object per command step, verify each step, and run no engine.

Contents: Message, start, signal: one mock per step · User task: stub the search, capture the filter · With a project wrapper

## Message, start, signal: one mock per step

```java
class LeasingProcessAdapterTest {

    private final CamundaClient camundaClient = mock(CamundaClient.class);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(camundaClient);

    private final PublishMessageCommandStep1 publishStep1 = mock(PublishMessageCommandStep1.class);
    private final PublishMessageCommandStep2 publishStep2 = mock(PublishMessageCommandStep2.class);
    private final PublishMessageCommandStep3 publishStep3 = mock(PublishMessageCommandStep3.class, RETURNS_SELF);
    private final CamundaFuture<PublishMessageResponse> publishFuture = mock();

    private final ApplicationId applicationId = ApplicationId.newId();

    @BeforeEach
    void stubPublishChain() {
        when(camundaClient.newPublishMessageCommand()).thenReturn(publishStep1);
        when(publishStep1.messageName(any())).thenReturn(publishStep2);
        when(publishStep2.correlationKey(any())).thenReturn(publishStep3);
        when(publishStep3.send()).thenReturn(publishFuture);
    }

    @Test
    @DisplayName("publishes contract signed with the application id as correlation key")
    void publishesContractSignedWithTheApplicationIdAsCorrelationKey() {
        underTest.notifyContractSigned(applicationId);

        verify(camundaClient).newPublishMessageCommand();
        verify(publishStep1).messageName(Messages.MIRAVELO_CONTRACT_SIGNED.getValue());
        verify(publishStep2).correlationKey(applicationId.value().toString());
        verify(publishStep3).timeToLive(Duration.ofSeconds(10));
        verify(publishStep3).send();
        verify(publishFuture).join();
        verifyNoMoreInteractions(camundaClient, publishStep1, publishStep2, publishStep3);
    }

    @Test
    @DisplayName("lets an engine failure reach the caller")
    void letsAnEngineFailureReachTheCaller() {
        when(publishFuture.join()).thenThrow(new ClientException("gateway unavailable"));

        assertThatThrownBy(() -> underTest.notifyContractSigned(applicationId)).isInstanceOf(ClientException.class);
    }
}
```

The step types are nested (`PublishMessageCommandStep1.PublishMessageCommandStep2`, `…Step3`); `RETURNS_SELF` covers the optional setters (`variables`, `timeToLive`, `messageId`) of the last step. Verify `variables(Map.of(…))` there when the adapter sends variables. Start (`newCreateInstanceCommand().bpmnProcessId(…).latestVersion()`) and signal (`newBroadcastSignalCommand().signalName(…)`) follow the same shape.

## User task: stub the search, capture the filter

```java
private final ArgumentCaptor<Consumer<UserTaskFilter>> filterCaptor = ArgumentCaptor.captor();

@Test
@DisplayName("completes the open clarification task of the application")
void completesTheOpenClarificationTaskOfTheApplication() {
    UserTask task = mock(UserTask.class);
    when(task.getUserTaskKey()).thenReturn(42L);
    stubOpenTasks(List.of(task));
    Variable variable = mock(Variable.class);
    when(variable.getName()).thenReturn(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue());
    when(variable.getValue()).thenReturn("\"" + applicationId.value() + "\"");
    UserTaskVariableSearchRequest variableSearch = mock(UserTaskVariableSearchRequest.class);
    CamundaFuture<SearchResponse<Variable>> variableFuture = mock();
    SearchResponse<Variable> variableResponse = mock();
    when(camundaClient.newUserTaskVariableSearchRequest(42L)).thenReturn(variableSearch);
    when(variableSearch.send()).thenReturn(variableFuture);
    when(variableFuture.join()).thenReturn(variableResponse);
    when(variableResponse.items()).thenReturn(List.of(variable));
    CompleteUserTaskCommandStep1 complete = mock(CompleteUserTaskCommandStep1.class, RETURNS_SELF);
    CamundaFuture<CompleteUserTaskResponse> completeFuture = mock();
    when(camundaClient.newCompleteUserTaskCommand(42L)).thenReturn(complete);
    when(complete.send()).thenReturn(completeFuture);

    underTest.completeAlternativeClarification(applicationId, true);

    verify(complete).variables(Map.of(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), true));
    verify(completeFuture).join();
    UserTaskFilter filter = mock(UserTaskFilter.class);
    filterCaptor.getValue().accept(filter);
    verify(filter).state(UserTaskState.CREATED);
    verify(filter).elementId(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
}

private void stubOpenTasks(List<UserTask> tasks) {
    UserTaskSearchRequest search = mock(UserTaskSearchRequest.class, RETURNS_SELF);
    CamundaFuture<SearchResponse<UserTask>> future = mock();
    SearchResponse<UserTask> response = mock();
    when(camundaClient.newUserTaskSearchRequest()).thenReturn(search);
    when(search.filter(filterCaptor.capture())).thenReturn(search);
    when(search.send()).thenReturn(future);
    when(future.join()).thenReturn(response);
    when(response.items()).thenReturn(tasks);
}
```

Add a "no open task" test with `stubOpenTasks(List.of())` and `assertThatThrownBy(…).isInstanceOf(NoSuchElementException.class)`. Zeebe returns variable values as JSON, so the stubbed string value is quoted. Types live in `io.camunda.client.api.search.{request,response,filter,enums}`.

## With a project wrapper

If the adapter calls a wrapper such as `ProcessEngineApi`, mock the wrapper instead: `verify(engineApi).sendMessage(Messages.MIRAVELO_CONTRACT_SIGNED, id.value().toString());` then `verifyNoMoreInteractions(engineApi)`. The wrapper takes the typed generated names, so expect those, not `.getValue()`.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/java-maven/service/common-zeebe/src/test/java/io/miragon/common/zeebe/engine/ProcessEngineApiTest.java
