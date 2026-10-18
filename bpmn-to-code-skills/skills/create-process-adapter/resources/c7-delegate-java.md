# Camunda 7 family, embedded · process adapter (Java)

Use this when the engine runs inside the Spring Boot app and the application drives it through `RuntimeService` and `TaskService`. It covers Camunda 7, CIB seven, Operaton and Fluxnova. Only the import root differs:

Contents: Ports (one per call) · Adapter · Business key versus correlation key · Transactions · Pitfalls

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

## Ports (one per call)

```java
public interface StartBikeLeasingPort {
    void startBikeLeasing(LeasingApplication application);
}

public interface NotifyContractSignedPort {
    void notifyContractSigned(ApplicationId id);
}

public interface CompleteAlternativeClarificationPort {
    void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId);
}
```

Give each method a distinct name: one adapter implements every port of the process, so two `notify(ApplicationId)` methods would clash. If the project has a per-process port (`LeasingProcess`), add the method there instead.

## Adapter

```java
@Component
public class BikeLeasingProcessAdapter
        implements StartBikeLeasingPort, NotifyContractSignedPort, CompleteAlternativeClarificationPort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public BikeLeasingProcessAdapter(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    public void startBikeLeasing(LeasingApplication application) {
        Map<String, Object> variables = Map.of(
                FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), application.id().value().toString(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), application.bikeId().value(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), application.monthlyNetIncome(),
                FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), application.age());
        runtimeService.startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(), application.id().value().toString(), variables);
    }

    @Override
    public void notifyContractSigned(ApplicationId id) {
        runtimeService.createMessageCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.getValue())
                .processInstanceBusinessKey(id.value().toString())
                .correlate();
    }

    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId) {
        Task task = taskService.createTaskQuery()
                .processInstanceBusinessKey(id.value().toString())
                .taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("No open clarify-alternative task for application " + id.value());
        }
        Map<String, Object> result = new HashMap<>();
        result.put(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), alternativeFound);
        if (bikeId != null) {
            result.put(FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.getValue(), bikeId.value());
        }
        taskService.complete(task.getId(), result);
    }
}
```

| Call | Engine API |
|---|---|
| start by message start event | `startProcessInstanceByMessage(Messages.X.getValue(), businessKey, variables)` |
| start by none start event | `startProcessInstanceByKey(BikeLeasingProcessApi.PROCESS_ID.getValue(), businessKey, variables)` |
| message to a waiting instance | `createMessageCorrelation(Messages.X.getValue()).processInstanceBusinessKey(key).setVariables(…).correlate()` |
| signal | `createSignalEvent(Signals.X.getValue()).setVariables(…).send()` |
| user task | `createTaskQuery().processInstanceBusinessKey(key).taskDefinitionKey(FlowNodes.<UserTask>.ELEMENT_ID)`, then `complete(task.getId(), variables)` |

```java
@Override
public void announceMemberActivated(MembershipId id) {
    runtimeService.createSignalEvent(Signals.MIRAVELO_MEMBER_ACTIVATED.getValue())
            .setVariables(Map.of(
                    WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables.MEMBERSHIP_ID.getValue(), id.value()))
            .send();
}
```

## Business key versus correlation key

- **Start with the domain id as business key.** Every later message, user-task query and process test then finds the instance by that key. The model needs no extra correlation variable.
- Camunda 7 models carry no correlation key. Correlate by business key, or by `processInstanceVariableEquals(name, value)` when one instance waits for messages about several domain ids.
- A signal has no correlation. It reaches every waiting instance and starts every process with a matching signal start event. Such a new instance has no business key unless a step sets one.

## Transactions

The embedded engine joins the caller's Spring transaction. Call the port inside the `@Transactional` service after the domain state is saved. The domain change and the engine command then commit or roll back together. The engine runs synchronously in the caller's thread up to the next wait state or async point. A failing delegate on that stretch fails the caller, and a slow one makes the caller slow. `camunda:asyncAfter="true"` on the start or catch event returns control right after the command.

## Pitfalls

- `correlate()` throws `MismatchingMessageCorrelationException` when no instance waits for the message, or when more than one does. Camunda 7 does not buffer messages. A message that arrives before its catch event is reached fails. Let it reach the caller or retry there; never swallow it.
- `Map.of` rejects `null` values. Use a `HashMap` for optional variables, as in the user-task method.
- `singleResult()` throws when more than one task matches (multi-instance user tasks). Narrow the query then.
- Pass plain values as variables (`String`, numbers, `Boolean`), never domain objects. The engine serializes objects into its database.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapter.java
