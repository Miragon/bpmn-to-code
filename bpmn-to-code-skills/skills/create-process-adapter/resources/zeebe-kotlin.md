# Zeebe · Process adapter (Kotlin)

Applies to Camunda 8 projects on the Camunda Spring SDK: the adapter in `adapter.outbound.zeebe` turns a port call into a `CamundaClient` command. If the project wraps the client (e.g. a `ProcessEngineApi` with `startProcess(PROCESS_ID, variables)` / `sendMessage(Messages.X, correlationKey, variables)` taking the typed names), call the wrapper instead.

Contents: Port · Adapter, start and message · Signal · User task completion · Pitfalls

## Port

New projects get one port per call, named after the business event: `interface NotifyContractSignedPort { fun notifyContractSigned(id: ApplicationId) }`. In a project with one port per process (`interface LeasingProcess`), add `fun correlateContractSigned(id: ApplicationId)` there instead.

## Adapter, start and message

```kotlin
@Component
class LeasingProcessAdapter(
    private val camundaClient: CamundaClient,
) : StartBikeLeasingPort, NotifyContractSignedPort {

    override fun startBikeLeasing(application: LeasingApplication) {
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        camundaClient.newPublishMessageCommand()
            .messageName(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value)
            .correlationKey(application.id.value.toString())
            .variables(
                mapOf(
                    start.APPLICATION_ID.value to application.id.value.toString(),
                    start.BIKE_ID.value to application.bikeId.value,
                    start.MONTHLY_NET_INCOME.value to application.monthlyNetIncome,
                    start.AGE.value to application.age,
                ),
            )
            .send()
            .join()
    }

    override fun notifyContractSigned(id: ApplicationId) {
        camundaClient.newPublishMessageCommand()
            .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.value)
            .correlationKey(id.value.toString())
            .timeToLive(Duration.ofSeconds(10))
            .send()
            .join()
    }
}
```

That `startBikeLeasing` is for a message start event. For a none start event, start by process id:

```kotlin
camundaClient.newCreateInstanceCommand()
    .bpmnProcessId(BikeLeasingProcessProcessApi.PROCESS_ID.value)
    .latestVersion()
    .variables(mapOf(start.APPLICATION_ID.value to application.id.value.toString()))
    .send()
    .join()
```

## Signal

```kotlin
camundaClient.newBroadcastSignalCommand()
    .signalName(Signals.MIRAVELO_MEMBER_ACTIVATED.value)
    .variables(mapOf(WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables.MEMBERSHIP_ID.value to membershipId.value))
    .send()
    .join()
```

## User task completion

Zeebe has no business key. Search the open tasks by the generated element id, match the domain id via the task's variables, then complete. `UserTaskState` is `io.camunda.client.api.search.enums.UserTaskState`.

```kotlin
override fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean) {
    val userTaskKey = findOpenClarificationTask(id)
    val output = FlowNodes.UserTaskClarifyAlternative.Variables
    camundaClient.newCompleteUserTaskCommand(userTaskKey)
        .variables(mapOf(output.ALTERNATIVE_FOUND.value to alternativeFound))
        .send()
        .join()
}

private fun findOpenClarificationTask(id: ApplicationId): Long {
    val openTasks = camundaClient.newUserTaskSearchRequest()
        .filter { filter ->
            filter.state(UserTaskState.CREATED)
            filter.elementId(FlowNodes.UserTaskClarifyAlternative.id.value)
        }
        .send()
        .join()
        .items()
    val task = openTasks.firstOrNull { readApplicationId(it.userTaskKey) == id.value.toString() }
        ?: throw NoSuchElementException("No open clarification task for application ${id.value}")
    return task.userTaskKey
}

private fun readApplicationId(userTaskKey: Long): String? {
    val applicationId = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID
    val variables = camundaClient.newUserTaskVariableSearchRequest(userTaskKey).send().join().items()
    return variables.firstOrNull { it.name == applicationId.value }?.value?.removeSurrounding("\"")
}
```

## Pitfalls

- Correlation key = domain id. The catch event declares `<zeebe:subscription correlationKey="=applicationId" />`, so publish exactly the string stored in that variable.
- Message start: with a correlation key, Zeebe keeps at most one active instance per key; a duplicate request starts nothing.
- Publishing never fails for "no subscription". With TTL 0 the message is dropped if the instance is not waiting yet; a TTL buffers it. `.messageId(…)` deduplicates retries within the TTL.
- Signals have no correlation and no buffer: every waiting signal event of every process gets them, a late one gets nothing.
- Searches are eventually consistent: a task created a moment ago may not be found yet, so surface `NoSuchElementException` as a domain error or retry. Commands are not affected. The `processInstanceVariables` filter exists, but on Elasticsearch it lags behind the task index, so the blueprint reads each task's variables (JSON, strings arrive quoted).
- Variable keys are `.value`; a `VariableName` key would serialize as an object.
- A Zeebe command cannot be rolled back. Call the port after the domain state is committed.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/outbound/zeebe/LeasingProcessAdapter.kt
