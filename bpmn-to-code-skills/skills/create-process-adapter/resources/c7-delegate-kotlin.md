# Camunda 7 family, embedded · process adapter (Kotlin)

Use this when the engine runs inside the Spring Boot app and the application drives it through `RuntimeService` and `TaskService`. It covers Camunda 7, CIB seven, Operaton and Fluxnova. Only the import root differs:

Contents: Ports (one per call) · Adapter · Business key versus correlation key · Transactions · Pitfalls

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

## Ports (one per call)

```kotlin
interface StartBikeLeasingPort {
    fun startBikeLeasing(application: LeasingApplication)
}

interface NotifyContractSignedPort {
    fun notifyContractSigned(id: ApplicationId)
}

interface CompleteAlternativeClarificationPort {
    fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean, bikeId: BikeId?)
}
```

Give each method a distinct name: one adapter implements every port of the process, so two `notify(id: ApplicationId)` methods would clash. If the project has a per-process port (`LeasingProcess`), add the method there instead.

## Adapter

```kotlin
@Component
class BikeLeasingProcessAdapter(
    private val runtimeService: RuntimeService,
    private val taskService: TaskService,
) : StartBikeLeasingPort, NotifyContractSignedPort, CompleteAlternativeClarificationPort {

    override fun startBikeLeasing(application: LeasingApplication) {
        val variables = FlowNodes.StartEventLeasingRequestReceived.Variables
        runtimeService.startProcessInstanceByMessage(
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
            application.id.value.toString(),
            mapOf(
                variables.APPLICATION_ID.value to application.id.value.toString(),
                variables.BIKE_ID.value to application.bikeId.value,
                variables.MONTHLY_NET_INCOME.value to application.monthlyNetIncome,
                variables.AGE.value to application.age,
            ),
        )
    }

    override fun notifyContractSigned(id: ApplicationId) {
        runtimeService.createMessageCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.value)
            .processInstanceBusinessKey(id.value.toString())
            .correlate()
    }

    override fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean, bikeId: BikeId?) {
        val taskDefinitionKey = FlowNodes.UserTaskClarifyAlternative.id.value
        val variables = FlowNodes.UserTaskClarifyAlternative.Variables
        val task = taskService.createTaskQuery()
            .processInstanceBusinessKey(id.value.toString())
            .taskDefinitionKey(taskDefinitionKey)
            .singleResult() ?: error("No open '$taskDefinitionKey' task for application ${id.value}")
        val result = buildMap {
            put(variables.ALTERNATIVE_FOUND.value, alternativeFound)
            bikeId?.let { put(variables.BIKE_ID.value, it.value) }
        }
        taskService.complete(task.id, result)
    }
}
```

| Call | Engine API |
|---|---|
| start by message start event | `startProcessInstanceByMessage(Messages.X.value, businessKey, variables)` |
| start by none start event | `startProcessInstanceByKey(BikeLeasingProcessApi.PROCESS_ID.value, businessKey, variables)` |
| message to a waiting instance | `createMessageCorrelation(Messages.X.value).processInstanceBusinessKey(key).setVariables(…).correlate()` |
| signal | `createSignalEvent(Signals.X.value).setVariables(…).send()` |
| user task | `createTaskQuery().processInstanceBusinessKey(key).taskDefinitionKey(FlowNodes.<UserTask>.id.value)`, then `complete(task.id, variables)` |

```kotlin
override fun announceMemberActivated(id: MembershipId) {
    val variables = WelcomePackageProcessApi.FlowNodes.StartEventMemberActivated.Variables
    runtimeService.createSignalEvent(Signals.MIRAVELO_MEMBER_ACTIVATED.value)
        .setVariables(mapOf(variables.MEMBERSHIP_ID.value to id.value))
        .send()
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
- `singleResult()` throws when more than one task matches (multi-instance user tasks). Narrow the query then.
- Pass plain values as variables (`String`, numbers, `Boolean`), never domain objects. The engine serializes objects into its database.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapter.kt
