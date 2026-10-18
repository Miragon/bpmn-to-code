# Camunda 7 family, remote · process adapter (Kotlin)

Applies when the engine runs in another process (Camunda 7, CIB seven, Operaton) and the service talks to it over the engine REST API (`/engine-rest`) through a client generated from the engine's OpenAPI spec (`<family>-engine-rest-openapi` artifact, OpenAPI Generator `kotlin` / `jvm-spring-restclient`). If the project hand-writes REST calls, keep its style but send the same payloads.

Contents: Port · Adapter · Rules · Pitfalls

| Family | Spec artifact | Generated client package (project choice) |
|---|---|---|
| CIB seven | `org.cibseven.bpm:cibseven-engine-rest-openapi` | `org.cibseven.rest.client.api` / `.model` |
| Operaton | `org.operaton.bpm:operaton-engine-rest-openapi` | `org.operaton.rest.client.api` / `.model` |
| Camunda 7 | `org.camunda.bpm:camunda-engine-rest-openapi` | whatever `apiPackage` the build sets |

## Port

```kotlin
interface NotifyContractSignedPort {
    fun notifyContractSigned(id: ApplicationId)
}
```

One port per call for new projects; with an existing per-process port (`LeasingProcess`) add a method there instead.

## Adapter

```kotlin
@Component
class BikeLeasingProcessAdapter(
    private val messageApi: MessageApi,
    private val taskApi: TaskApi,
) : StartBikeLeasingPort, NotifyContractSignedPort, CompleteAlternativeClarificationPort {

    override fun startBikeLeasing(application: LeasingApplication) {
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        val message = CorrelationMessageDto(
            messageName = Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
            businessKey = application.id.value.toString(),
            processVariables = mapOf(
                start.BIKE_ID.value to typedVar(application.bikeId.value),
                start.AGE.value to typedVar(application.age),
            ),
        )
        messageApi.deliverMessageWithHttpInfo(message)
    }

    override fun notifyContractSigned(id: ApplicationId) {
        val message = CorrelationMessageDto(
            messageName = Messages.MIRAVELO_CONTRACT_SIGNED.value,
            businessKey = id.value.toString(),
        )
        messageApi.deliverMessageWithHttpInfo(message)
    }

    override fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean) {
        val taskDefinitionKey = FlowNodes.UserTaskClarifyAlternative.id.value
        val tasks = taskApi.getTasks(
            processInstanceBusinessKey = id.value.toString(),
            taskDefinitionKey = taskDefinitionKey,
        )
        val taskId = tasks.firstOrNull()?.id ?: error("No open task '$taskDefinitionKey' for application ${id.value}")
        val output = FlowNodes.UserTaskClarifyAlternative.Variables
        val variables = mapOf(output.ALTERNATIVE_FOUND.value to typedVar(alternativeFound))
        taskApi.completeWithHttpInfo(taskId, CompleteTaskDto(variables = variables))
    }

    private fun typedVar(value: Any): VariableValueDto {
        val type = when (value) {
            is String -> "String"
            is Boolean -> "Boolean"
            is Int -> "Integer"
            is Long -> "Long"
            is Double -> "Double"
            else -> error("Unsupported process variable type: ${value::class}")
        }
        return VariableValueDto(value = value, type = type)
    }
}
```

A signal goes into the adapter of the process that catches it, here `WelcomePackageProcessAdapter` (`POST /signal`):

```kotlin
override fun announceMemberActivated(id: MembershipId) {
    val start = WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables
    val variables = mapOf(start.MEMBERSHIP_ID.value to typedVar(id.value))
    signalApi.throwSignal(SignalDto(name = Signals.MIRAVELO_MEMBER_ACTIVATED.value, variables = variables))
}
```

Place adapters in `adapter.outbound.engine`. Wire each API as a bean over one `RestClient` with the engine base URL: `@Bean fun messageApi(engineRestClient: RestClient) = MessageApi(engineRestClient)`, same for `TaskApi`, `SignalApi` and `ProcessDefinitionApi`.

## Rules

- Start a process with a message start event through `POST /message` (`businessKey` = domain id). For a plain none start event use `ProcessDefinitionApi.startProcessInstanceByKey(<Process>ProcessApi.PROCESS_ID.value, StartProcessInstanceDto(businessKey = …, variables = …))`.
- Correlate by business key. Use `correlationKeys` (process variables, typed) only when the business key cannot identify the instance.
- Always set `type` on every variable, so the engine stores the type the model expects instead of inferring one from the JSON value.
- A signal reaches every subscribed instance and signal start event, it has no business key. Use it only for broadcasts.
- Look up a user task by `processInstanceBusinessKey` + `taskDefinitionKey`. The query returns an empty list, not a 404, when no task is open: fail with a clear exception.

## Pitfalls

- No shared transaction: the REST call is not rolled back with the database. Call the port after the domain state is committed (`@TransactionalEventListener(phase = AFTER_COMMIT)` or after the service's transaction).
- HTTP errors surface as `RestClientResponseException`. `/message` answers `400` when the message matches no waiting instance or more than one. Do not swallow it.
- The client does not retry, and a timeout leaves the outcome unknown. Repeating a correlation that already went through also fails with `400`, because nothing waits anymore.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/kotlin-gradle/service/example-service/src/main/kotlin/io/miragon/blueprint/adapter/outbound/engine/RemoteLeasingProcessAdapter.kt
