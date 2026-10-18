# process-engine-api · Process adapter (Java)

Applies to projects on `dev.bpm-crafters.process-engine-api` with any engine adapter: Camunda 7, CIB seven, Operaton (embedded or remote) or Camunda 8. The commands are engine-neutral. Only the correlation differs per engine, see [Correlation per engine](#correlation-per-engine).

Contents: Port and adapter · Start · Message · Signal · User task · Transactions (embedded C7 family) · Correlation per engine

## Port and adapter

```java
public interface NotifyContractSignedPort {
    void notifyContractSigned(ApplicationId id);
}
```

`BikeLeasingProcessAdapter` (`@Component` in `adapter.outbound.<engine>`) implements every port of the process. It injects only what it uses: `StartProcessApi`, `CorrelationApi`, `SignalApi`, `UserTaskSupport`, `UserTaskCompletionApi`. Every call returns a `CompletableFuture`, so always `join()` it. Without `join()` a failure goes unnoticed.

## Start

```java
@Override
public void submitRequest(LeasingApplication application) {
    String key = application.id().value().toString();
    Map<String, Object> payload = new HashMap<>();
    payload.put(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), key);
    payload.put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), application.bikeId().value());
    payload.put(CommonRestrictions.BUSINESS_KEY, key);
    payload.put(CommonRestrictions.CORRELATION_KEY, key);
    var command = new StartProcessByMessageCmd(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(), payload);
    startProcessApi.startProcess(command).join();
}
```

- For a none start event: `new StartProcessByDefinitionCmd(BikeLeasingProcessProcessApi.PROCESS_ID.getValue(), payload)`.
- `BUSINESS_KEY` and `CORRELATION_KEY` go into the **payload**, not the restrictions. The C7-family adapters take the business key from it, and `correlationKey` becomes the instance variable that `useGlobalCorrelationKey` matches later. On C8 both are plain variables.

## Message

```java
@Override
public void notifyContractSigned(ApplicationId id) {
    var command = new CorrelateMessageCmd(
            Messages.MIRAVELO_CONTRACT_SIGNED.getValue(),
            Map.of(),
            Correlation.withKey(id.value().toString()),
            CommonRestrictions.builder().withRestriction("useGlobalCorrelationKey", "true").build());
    try {
        correlationApi.correlateMessage(command).join();
    } catch (CompletionException e) {
        throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
}
```

`join()` wraps the engine failure in a `CompletionException`, e.g. `MismatchingMessageCorrelationException` when no instance waits. Rethrow an unchecked cause so callers and the REST advice see the real exception.

## Signal

In the adapter of the process that catches the signal (here the membership process):

```java
@Override
public void broadcastMemberActivated() {
    var command = new SendSignalCmd(Signals.MIRAVELO_MEMBER_ACTIVATED.getValue(), Map.of(), Map.of());
    signalApi.sendSignal(command).join();
}
```

The arguments are signal name, restrictions, payload, in that order. `SendSignalCmd` and `SignalApi` live in `dev.bpmcrafters.processengineapi.correlation`. A signal reaches every waiting instance and starts every process with a matching signal start event. There is no correlation.

## User task

The API has no task query. A `UserTaskSupport` bean collects the delivered tasks of one element, and the adapter resolves the task id from it. Declare the beans in a `@Configuration` (blueprint: `adapter/process/EngineApiConfiguration`):

```java
@Bean
public UserTaskSupport clarifyAlternativeUserTaskSupport(TaskSubscriptionApi taskSubscriptionApi) {
    UserTaskSupport support = new UserTaskSupport();
    String elementId = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID;
    support.subscribe(taskSubscriptionApi, CommonRestrictions.builder().build(), elementId, null);
    return support;
}

@Override
public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound) {
    String elementId = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID;
    String applicationIdKey = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();
    String applicationId = id.value().toString();
    TaskInformation task = userTaskSupport.getAllTasks().stream()
            .filter(it -> elementId.equals(it.getMeta().get(CommonRestrictions.ACTIVITY_ID)))
            .filter(it -> applicationId.equals(userTaskSupport.getPayload(it.getTaskId()).get(applicationIdKey)))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No open " + elementId + " task for " + id.value()));
    String output = FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue();
    userTaskCompletionApi.completeTask(new CompleteTaskCmd(task.getTaskId(), Map.of(output, alternativeFound))).join();
}
```

The adapter's user-task delivery must be on (blueprint: `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.user-tasks.delivery-strategy: embedded_scheduled`). It runs on a schedule, so a task that just opened may be missing from the pool: the blueprint retries the lookup (50 × 200 ms) before it fails.

## Transactions (embedded C7 family)

```java
@Bean
public EngineCommandExecutor engineCommandExecutor() { return new EngineCommandExecutor(Runnable::run); }
```

By default, commands run on `ForkJoinPool.commonPool()`, outside the caller's transaction. This bean runs them on the caller's thread, so engine state and business data commit or roll back together. The package depends on the adapter, e.g. `dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared`. Remote adapters and C8 have no shared transaction: call the port after the commit.

## Correlation per engine

| Adapter | `Correlation.withKey(key)` matches | The model or the start must provide |
|---|---|---|
| Camunda 8 | the message's native correlation key | the subscription's `correlationKey` expression (e.g. `=applicationId`). Only `tenantId` is allowed as a restriction; `useGlobalCorrelationKey` fails the call. |
| Camunda 7, CIB seven, Operaton (embedded and remote), no restriction | a **local** variable `correlationKey` on the catching element | an input mapping that sets `correlationKey`. Use `.withVariable("x")` for another name. |
| same, with `useGlobalCorrelationKey = "true"` | the **process instance** variable `correlationKey` | `CommonRestrictions.CORRELATION_KEY` in the start payload (the blueprint's choice) |

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapter.java
