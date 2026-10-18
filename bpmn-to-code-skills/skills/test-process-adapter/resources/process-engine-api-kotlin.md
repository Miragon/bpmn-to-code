# process-engine-api · Process adapter test (Kotlin)

Applies to adapters that call `StartProcessApi`, `CorrelationApi`, `SignalApi` or `UserTaskCompletionApi` (`dev.bpm-crafters.process-engine-api`), whatever engine adapter runs below. Mock the APIs, capture the command with a MockK `slot` and assert every field of it. Stub the futures with `CompletableFuture.completedFuture(...)`.

## Test

```kotlin
class BikeLeasingProcessAdapterTest {

    private val startProcessApi = mockk<StartProcessApi>()
    private val correlationApi = mockk<CorrelationApi>()
    private val userTaskSupport = mockk<UserTaskSupport>()
    private val userTaskCompletionApi = mockk<UserTaskCompletionApi>()
    private val underTest =
        BikeLeasingProcessAdapter(startProcessApi, correlationApi, userTaskSupport, userTaskCompletionApi)

    private val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val key = id.value.toString()

    @Test
    fun `submitRequest starts the process by message with the start variables and keys`() {
        val cmd = slot<StartProcessByMessageCmd>()
        every { startProcessApi.startProcess(capture(cmd)) } returns
            CompletableFuture.completedFuture(mockk<ProcessInformation>())

        underTest.submitRequest(testLeasingApplication(id = id, bikeId = BikeId("BIKE-900")))

        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        assertThat(cmd.captured.messageName).isEqualTo(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value)
        assertThat(cmd.captured.get()).isEqualTo(
            mapOf(
                start.APPLICATION_ID.value to key,
                start.BIKE_ID.value to "BIKE-900",
                CommonRestrictions.BUSINESS_KEY to key,
                CommonRestrictions.CORRELATION_KEY to key,
            ),
        )
        verify { startProcessApi.startProcess(any()) }
        confirmVerified(startProcessApi)
    }

    @Test
    fun `notifyContractSigned correlates the message by the application id`() {
        val cmd = slot<CorrelateMessageCmd>()
        every { correlationApi.correlateMessage(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        underTest.notifyContractSigned(id)

        assertThat(cmd.captured.messageName).isEqualTo(Messages.MIRAVELO_CONTRACT_SIGNED.value)
        assertThat(cmd.captured.correlation.get().correlationKey).isEqualTo(key)
        assertThat(cmd.captured.restrictions).isEqualTo(mapOf("useGlobalCorrelationKey" to "true"))
        assertThat(cmd.captured.get()).isEmpty()
        verify { correlationApi.correlateMessage(any()) }
        confirmVerified(correlationApi)
    }

    @Test
    fun `completeAlternativeClarification completes the task of this application`() {
        val task = TaskInformation(
            taskId = "task-1",
            meta = mapOf(CommonRestrictions.ACTIVITY_ID to FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID),
        )
        every { userTaskSupport.getAllTasks() } returns listOf(task)
        every { userTaskSupport.getPayload("task-1") } returns
            mapOf(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value to key)
        val cmd = slot<CompleteTaskCmd>()
        every { userTaskCompletionApi.completeTask(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        underTest.completeAlternativeClarification(id, alternativeFound = true)

        assertThat(cmd.captured.taskId).isEqualTo("task-1")
        val output = FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND
        assertThat(cmd.captured.get()).isEqualTo(mapOf(output.value to true))
        verify { userTaskCompletionApi.completeTask(any()) }
        confirmVerified(userTaskCompletionApi)
    }

    @Test
    fun `a failed correlation surfaces the engine exception unwrapped`() {
        val failure = IllegalStateException("no instance waits for the message")
        every { correlationApi.correlateMessage(any()) } returns CompletableFuture.failedFuture(failure)

        assertThatThrownBy { underTest.notifyContractSigned(id) }.isSameAs(failure)
    }
}
```

- Imports: `dev.bpmcrafters.processengineapi.Empty` (an `object`), `ProcessInformation` from `...processengineapi.process`, `TaskInformation`, `CompleteTaskCmd` and `UserTaskCompletionApi` from `...processengineapi.task`, `UserTaskSupport` from `...processengineapi.task.support`.
- Payload and correlation sit behind suppliers: read them with `cmd.captured.get()` and `cmd.captured.correlation.get()`.
- Compare the whole payload with `isEqualTo(mapOf(...))` keyed by the generated names, so an extra or renamed variable fails.
- Start by definition: assert `definitionKey == BikeLeasingProcessProcessApi.PROCESS_ID.value`. Signal: capture `SendSignalCmd`, assert `signalName == Signals.X.value`, `restrictions` and `get()`.
- User task: add a test with an empty pool or a task of another application. The adapter must fail and `completeTask` must not be called (`verify(exactly = 0)`).
- A test that stubs `failedFuture(...)` proves the adapter joins and does not swallow the error.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/kotlin-gradle/service/app/src/test/kotlin/io/miragon/blueprint/adapter/outbound/cibseven/LeasingProcessAdapterTest.kt
