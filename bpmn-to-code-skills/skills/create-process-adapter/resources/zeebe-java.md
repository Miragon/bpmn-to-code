# Zeebe · Process adapter (Java)

Applies to Camunda 8 projects on the Camunda Spring SDK: the adapter in `adapter.outbound.zeebe` turns a port call into a `CamundaClient` command. If the project wraps the client (e.g. a `ProcessEngineApi` with `startProcess(PROCESS_ID, variables)` / `sendMessage(Messages.X, correlationKey, variables)` taking the typed names), call the wrapper instead.

Contents: Port · Adapter, start and message · Signal · User task completion · Pitfalls

## Port

New projects get one port per call, named after the business event: `public interface NotifyContractSignedPort { void notifyContractSigned(ApplicationId id); }`. In a project with one port per process (`interface LeasingProcess`), add `void correlateContractSigned(ApplicationId id);` there instead.

## Adapter, start and message

```java
@Component
public class LeasingProcessAdapter implements StartBikeLeasingPort, NotifyContractSignedPort {

    private final CamundaClient camundaClient;

    public LeasingProcessAdapter(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    @Override
    public void startBikeLeasing(LeasingApplication application) {
        String applicationId = application.id().value().toString();
        camundaClient.newPublishMessageCommand()
            .messageName(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue())
            .correlationKey(applicationId)
            .variables(Map.of(
                FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), applicationId,
                FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), application.bikeId().value(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), application.monthlyNetIncome(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), application.age()))
            .send()
            .join();
    }

    @Override
    public void notifyContractSigned(ApplicationId id) {
        camundaClient.newPublishMessageCommand()
            .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.getValue())
            .correlationKey(id.value().toString())
            .timeToLive(Duration.ofSeconds(10))
            .send()
            .join();
    }
}
```

That `startBikeLeasing` is for a message start event. For a none start event, start by process id:

```java
camundaClient.newCreateInstanceCommand()
    .bpmnProcessId(BikeLeasingProcessProcessApi.PROCESS_ID.getValue())
    .latestVersion()
    .variables(Map.of(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), applicationId))
    .send()
    .join();
```

## Signal

```java
camundaClient.newBroadcastSignalCommand()
    .signalName(Signals.MIRAVELO_MEMBER_ACTIVATED.getValue())
    .variables(Map.of(WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables.MEMBERSHIP_ID.getValue(), membershipId.value()))
    .send()
    .join();
```

## User task completion

Zeebe has no business key. Search the open tasks by the generated element id, match the domain id via the task's variables, then complete. Types: `io.camunda.client.api.search.enums.UserTaskState`, `io.camunda.client.api.search.response.UserTask` / `Variable`.

```java
@Override
public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound) {
    long userTaskKey = findOpenClarificationTask(id);
    camundaClient.newCompleteUserTaskCommand(userTaskKey)
        .variables(Map.of(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), alternativeFound))
        .send()
        .join();
}

private long findOpenClarificationTask(ApplicationId id) {
    List<UserTask> openTasks = camundaClient.newUserTaskSearchRequest()
        .filter(filter -> {
            filter.state(UserTaskState.CREATED);
            filter.elementId(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        })
        .send()
        .join()
        .items();
    String applicationId = id.value().toString();
    return openTasks.stream()
        .filter(task -> readApplicationId(task.getUserTaskKey()).filter(applicationId::equals).isPresent())
        .findFirst()
        .map(UserTask::getUserTaskKey)
        .orElseThrow(() -> new NoSuchElementException("No open clarification task for application " + applicationId));
}

private Optional<String> readApplicationId(long userTaskKey) {
    String applicationIdVariable = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();
    return camundaClient.newUserTaskVariableSearchRequest(userTaskKey).send().join().items().stream()
        .filter(variable -> applicationIdVariable.equals(variable.getName()))
        .findFirst()
        .map(Variable::getValue)
        .map(value -> value.replaceAll("^\"|\"$", ""));
}
```

## Pitfalls

- Correlation key = domain id. The catch event declares `<zeebe:subscription correlationKey="=applicationId" />`, so publish exactly the string stored in that variable.
- Message start: with a correlation key, Zeebe keeps at most one active instance per key; a duplicate request starts nothing.
- Publishing never fails for "no subscription". With TTL 0 the message is dropped if the instance is not waiting yet; a TTL buffers it. `.messageId(…)` deduplicates retries within the TTL.
- Signals have no correlation and no buffer: every waiting signal event of every process gets them, a late one gets nothing.
- Searches are eventually consistent: a task created a moment ago may not be found yet, so surface `NoSuchElementException` as a domain error or retry. Commands are not affected. The `processInstanceVariables` filter exists, but on Elasticsearch it lags behind the task index, so the blueprint reads each task's variables (JSON, strings arrive quoted).
- Variable keys are `.getValue()`; a `VariableName` key would serialize as an object. `Map.of` rejects `null` values, so build a `LinkedHashMap` for optional ones.
- A Zeebe command cannot be rolled back. Call the port after the domain state is committed.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/outbound/zeebe/LeasingProcessAdapter.java
