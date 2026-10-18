# Camunda 7 family, embedded · process adapter unit test (Kotlin)

Use this for an adapter that calls `RuntimeService` / `TaskService` of Camunda 7, CIB seven, Operaton or Fluxnova. The test mocks the services and their fluent builders with MockK and starts no engine.

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

Engine types used: `RuntimeService`, `TaskService`, `runtime.MessageCorrelationBuilder`, `runtime.SignalEventReceivedBuilder`, `task.TaskQuery`, `task.Task`, `MismatchingMessageCorrelationException`.

## Test

```kotlin
class BikeLeasingProcessAdapterTest {

    private val runtimeService = mockk<RuntimeService>()
    private val taskService = mockk<TaskService>()
    private val underTest = BikeLeasingProcessAdapter(runtimeService, taskService)

    private val applicationId = ApplicationId.new()
    private val businessKey = applicationId.value.toString()

    @Test
    fun `startBikeLeasing starts the process by message with the application id as business key`() {
        val application = testLeasingApplication(id = applicationId)
        val variables = FlowNodes.StartEventLeasingRequestReceived.Variables
        every { runtimeService.startProcessInstanceByMessage(any(), any<String>(), any()) } returns mockk()

        underTest.startBikeLeasing(application)

        val expectedVariables = mapOf(
            variables.APPLICATION_ID.value to businessKey,
            variables.BIKE_ID.value to application.bikeId.value,
            variables.MONTHLY_NET_INCOME.value to application.monthlyNetIncome,
            variables.AGE.value to application.age,
        )
        verify {
            runtimeService.startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value, businessKey, expectedVariables,
            )
        }
        confirmVerified(runtimeService)
    }

    @Test
    fun `notifyContractSigned correlates the message by business key`() {
        val correlation = givenCorrelation()
        every { correlation.correlate() } just Runs

        underTest.notifyContractSigned(applicationId)

        verify { runtimeService.createMessageCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.value) }
        verify { correlation.processInstanceBusinessKey(businessKey) }
        verify { correlation.correlate() }
        confirmVerified(runtimeService, correlation)
    }

    @Test
    fun `notifyContractSigned lets a failed correlation reach the caller`() {
        val correlation = givenCorrelation()
        every { correlation.correlate() } throws MismatchingMessageCorrelationException("no waiting instance")

        assertThatThrownBy { underTest.notifyContractSigned(applicationId) }
            .isInstanceOf(MismatchingMessageCorrelationException::class.java)
    }

    @Test
    fun `completeAlternativeClarification completes the open task of the application`() {
        val query = givenTaskQuery(returning = mockk<Task> { every { id } returns "task-1" })
        val variables = FlowNodes.UserTaskClarifyAlternative.Variables
        every { taskService.complete(any(), any()) } just Runs

        underTest.completeAlternativeClarification(applicationId, alternativeFound = true, bikeId = BikeId("BIKE-42"))

        verify { query.processInstanceBusinessKey(businessKey) }
        verify { query.taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.id.value) }
        verify { taskService.createTaskQuery() }
        verify {
            taskService.complete("task-1", mapOf(variables.ALTERNATIVE_FOUND.value to true, variables.BIKE_ID.value to "BIKE-42"))
        }
        confirmVerified(taskService)
    }

    @Test
    fun `completeAlternativeClarification fails when no task is open`() {
        givenTaskQuery(returning = null)

        assertThatThrownBy { underTest.completeAlternativeClarification(applicationId, true, null) }
            .isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 0) { taskService.complete(any(), any()) }
    }

    private fun givenCorrelation(): MessageCorrelationBuilder {
        val correlation = mockk<MessageCorrelationBuilder>()
        every { runtimeService.createMessageCorrelation(any()) } returns correlation
        every { correlation.processInstanceBusinessKey(any()) } returns correlation
        return correlation
    }

    private fun givenTaskQuery(returning: Task?): TaskQuery {
        val query = mockk<TaskQuery>()
        every { taskService.createTaskQuery() } returns query
        every { query.processInstanceBusinessKey(any()) } returns query
        every { query.taskDefinitionKey(any()) } returns query
        every { query.singleResult() } returns returning
        return query
    }
}
```

## Notes

- Compare whole variable maps with `mapOf(...)`, not with `match { it["age"] == 35 }`. Then a missing or extra variable fails the test.
- `correlate()`, `send()` and `complete(...)` return `void`: stub them with `just Runs`. `startProcessInstanceByMessage` returns a `ProcessInstance`: `returns mockk()`.
- For a signal, stub `createSignalEvent(any())` to return a `mockk<SignalEventReceivedBuilder>()` whose `setVariables(any())` returns itself. Verify `createSignalEvent(Signals.X.value)`, `setVariables(expectedMap)` and `send()`.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapterTest.kt
