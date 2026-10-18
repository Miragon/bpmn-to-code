# Camunda 7 family, remote · process adapter (Java)

Applies when the engine runs in another process (Camunda 7, CIB seven, Operaton) and the service talks to it over the engine REST API (`/engine-rest`) through a client generated from the engine's OpenAPI spec (`<family>-engine-rest-openapi` artifact, OpenAPI Generator `java` / `restclient`). If the project hand-writes REST calls, keep its style but send the same payloads.

Contents: Port · Adapter · Rules · Pitfalls

| Family | Spec artifact | Generated client package (project choice) |
|---|---|---|
| CIB seven | `org.cibseven.bpm:cibseven-engine-rest-openapi` | `org.cibseven.rest.client.api` / `.model` |
| Operaton | `org.operaton.bpm:operaton-engine-rest-openapi` | `org.operaton.rest.client.api` / `.model` |
| Camunda 7 | `org.camunda.bpm:camunda-engine-rest-openapi` | whatever `apiPackage` the build sets |

## Port

```java
public interface NotifyContractSignedPort {
    void notifyContractSigned(ApplicationId id);
}
```

One port per call for new projects; with an existing per-process port (`LeasingProcess`) add a method there instead.

## Adapter

```java
@Component
public class BikeLeasingProcessAdapter
        implements StartBikeLeasingPort, NotifyContractSignedPort, CompleteAlternativeClarificationPort {

    private final MessageApi messageApi;
    private final TaskApi taskApi;

    public BikeLeasingProcessAdapter(MessageApi messageApi, TaskApi taskApi) {
        this.messageApi = messageApi;
        this.taskApi = taskApi;
    }

    @Override
    public void startBikeLeasing(LeasingApplication application) {
        Map<String, VariableValueDto> variables = new LinkedHashMap<>();
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), typedVar(application.bikeId().value()));
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), typedVar(application.age()));
        messageApi.deliverMessageWithHttpInfo(new CorrelationMessageDto()
                .messageName(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue())
                .businessKey(application.id().value().toString())
                .processVariables(variables));
    }

    @Override
    public void notifyContractSigned(ApplicationId id) {
        messageApi.deliverMessageWithHttpInfo(new CorrelationMessageDto()
                .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.getValue())
                .businessKey(id.value().toString()));
    }

    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound) {
        String taskDefinitionKey = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID;
        String taskId = taskApi.getTasks(new TaskApi.GetTasksRequest()
                        .processInstanceBusinessKey(id.value().toString())
                        .taskDefinitionKey(taskDefinitionKey))
                .stream().findFirst().map(TaskWithAttachmentAndCommentDto::getId)
                .orElseThrow(() -> new IllegalStateException("No open task '" + taskDefinitionKey + "' for " + id.value()));
        Map<String, VariableValueDto> variables = Map.of(
                FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), typedVar(alternativeFound));
        taskApi.completeWithHttpInfo(taskId, new CompleteTaskDto().variables(variables));
    }

    private VariableValueDto typedVar(Object value) {
        String type = switch (value) {
            case String s -> "String";
            case Boolean b -> "Boolean";
            case Integer i -> "Integer";
            case Long l -> "Long";
            case Double d -> "Double";
            default -> throw new IllegalStateException("Unsupported process variable type: " + value.getClass());
        };
        return new VariableValueDto().value(value).type(type);
    }
}
```

`TaskApi.GetTasksRequest` exists only with `useSingleRequestParameter=static`; without it `getTasks` takes ~100 positional parameters, so set that option in the generator config. A signal goes into the adapter of the process that catches it, here `WelcomePackageProcessAdapter` (`POST /signal`):

```java
@Override
public void announceMemberActivated(MembershipId id) {
    String membershipId = WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables.MEMBERSHIP_ID.getValue();
    signalApi.throwSignal(new SignalDto()
            .name(Signals.MIRAVELO_MEMBER_ACTIVATED.getValue())
            .putVariablesItem(membershipId, typedVar(id.value())));
}
```

Place adapters in `adapter.outbound.engine`. Wire one `ApiClient` bean (`new ApiClient(restClient).setBasePath(baseUrl)`) and one bean per API on top of it (`new MessageApi(apiClient)`, same for `TaskApi`, `SignalApi`).

## Rules

- Start a process with a message start event through `POST /message` (`businessKey` = domain id). For a plain none start event use `ProcessDefinitionApi.startProcessInstanceByKey` with `<Process>ProcessApi.PROCESS_ID.getValue()`.
- Correlate by business key. Use `correlationKeys` (process variables, typed) only when the business key cannot identify the instance.
- Always set `type` on every variable, so the engine stores the type the model expects instead of inferring one from the JSON value.
- A signal reaches every subscribed instance and signal start event, it has no business key. Use it only for broadcasts.
- The task query returns an empty list, not a 404, when no task is open: fail with a clear exception.

## Pitfalls

- No shared transaction: the REST call is not rolled back with the database. Call the port after the domain state is committed (`@TransactionalEventListener(phase = AFTER_COMMIT)` or after the service's transaction).
- HTTP errors surface as `RestClientResponseException`. `/message` answers `400` when the message matches no waiting instance or more than one. Do not swallow it.
- The client does not retry, and a timeout leaves the outcome unknown. Repeating a correlation that already went through also fails with `400`, because nothing waits anymore.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/java-maven/service/example-service/src/main/java/io/miragon/blueprint/adapter/outbound/engine/RemoteLeasingProcessAdapter.java
