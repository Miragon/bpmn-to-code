# process-engine-api · Process adapter (Kotlin)

Applies to projects on `dev.bpm-crafters.process-engine-api` with any engine adapter: Camunda 7, CIB seven, Operaton (embedded or remote) or Camunda 8. The commands are engine-neutral. Only the correlation differs per engine, see [Correlation per engine](#correlation-per-engine).

Contents: Port and adapter · Start · Message · Signal · User task · Transactions (embedded C7 family) · Correlation per engine

## Port and adapter

```kotlin
interface NotifyContractSignedPort {
    fun notifyContractSigned(id: ApplicationId)
}
```

`BikeLeasingProcessAdapter` (`@Component` in `adapter.outbound.<engine>`) implements every port of the process. It injects only what it uses: `StartProcessApi`, `CorrelationApi`, `SignalApi`, `UserTaskSupport`, `UserTaskCompletionApi`. Every call returns a `CompletableFuture`, so always `join()` it. Without `join()` a failure goes unnoticed.

## Start

```kotlin
override fun submitRequest(application: LeasingApplication) {
    val start = FlowNodes.StartEventLeasingRequestReceived.Variables
    val key = application.id.value.toString()
    val payload = mapOf(
        start.APPLICATION_ID.value to key,
        start.BIKE_ID.value to application.bikeId.value,
        CommonRestrictions.BUSINESS_KEY to key,
        CommonRestrictions.CORRELATION_KEY to key,
    )
    val command = StartProcessByMessageCmd(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value, payload)
    startProcessApi.startProcess(command).join()
}
```

- For a none start event: `StartProcessByDefinitionCmd(BikeLeasingProcessProcessApi.PROCESS_ID.value, payload)`.
- `BUSINESS_KEY` and `CORRELATION_KEY` go into the **payload**, not the restrictions. The C7-family adapters take the business key from it, and `correlationKey` becomes the instance variable that `useGlobalCorrelationKey` matches later. On C8 both are plain variables.

## Message

```kotlin
override fun notifyContractSigned(id: ApplicationId) {
    val command = CorrelateMessageCmd(
        messageName = Messages.MIRAVELO_CONTRACT_SIGNED.value,
        payload = emptyMap(),
        correlation = Correlation.withKey(id.value.toString()),
        restrictions = CommonRestrictions.builder().withRestriction("useGlobalCorrelationKey", "true").build(),
    )
    try {
        correlationApi.correlateMessage(command).join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }
}
```

`join()` wraps the engine failure in a `CompletionException`, e.g. `MismatchingMessageCorrelationException` when no instance waits. Rethrow the cause so callers and the REST advice see the real exception.

## Signal

In the adapter of the process that catches the signal (here the membership process):

```kotlin
override fun broadcastMemberActivated() {
    val command = SendSignalCmd(signalName = Signals.MIRAVELO_MEMBER_ACTIVATED.value, restrictions = emptyMap())
    signalApi.sendSignal(command).join()
}
```

A signal reaches every waiting instance and starts every process with a matching signal start event. There is no correlation. For variables, add `payload = mapOf(...)`.

## User task

The API has no task query. A `UserTaskSupport` bean collects the delivered tasks of one element, and the adapter resolves the task id from it. Declare the beans in a `@Configuration` (blueprint: `adapter/process/EngineApiConfiguration`):

```kotlin
@Bean
fun clarifyAlternativeUserTaskSupport(taskSubscriptionApi: TaskSubscriptionApi) = UserTaskSupport().apply {
    subscribe(taskSubscriptionApi = taskSubscriptionApi, taskDescriptionKey = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
}

override fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean) {
    val elementId = FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID
    val applicationIdKey = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value
    val task = userTaskSupport.getAllTasks()
        .filter { it.meta[CommonRestrictions.ACTIVITY_ID] == elementId }
        .firstOrNull { userTaskSupport.getPayload(it.taskId)[applicationIdKey] == id.value.toString() }
        ?: error("No open $elementId task for application ${id.value}")
    val output = FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND
    userTaskCompletionApi.completeTask(CompleteTaskCmd(task.taskId, mapOf(output.value to alternativeFound))).join()
}
```

The adapter's user-task delivery must be on (blueprint: `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.user-tasks.delivery-strategy: embedded_scheduled`). It runs on a schedule, so a task that just opened may be missing from the pool: the blueprint retries the lookup (50 × 200 ms) before it fails.

## Transactions (embedded C7 family)

```kotlin
@Bean
fun engineCommandExecutor() = EngineCommandExecutor(executor = { it.run() })
```

By default, commands run on `ForkJoinPool.commonPool()`, outside the caller's transaction. This bean runs them on the caller's thread, so engine state and business data commit or roll back together. The package depends on the adapter, e.g. `dev.bpmcrafters.processengineapi.adapter.cibseven.embedded.shared`. Remote adapters and C8 have no shared transaction: call the port after the commit.

## Correlation per engine

| Adapter | `Correlation.withKey(key)` matches | The model or the start must provide |
|---|---|---|
| Camunda 8 | the message's native correlation key | the subscription's `correlationKey` expression (e.g. `=applicationId`). Only `tenantId` is allowed as a restriction; `useGlobalCorrelationKey` fails the call. |
| Camunda 7, CIB seven, Operaton (embedded and remote), no restriction | a **local** variable `correlationKey` on the catching element | an input mapping that sets `correlationKey`. Use `.withVariable("x")` for another name. |
| same, with `useGlobalCorrelationKey = "true"` | the **process instance** variable `correlationKey` | `CommonRestrictions.CORRELATION_KEY` in the start payload (the blueprint's choice) |

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapter.kt
