# Zeebe · Process adapter unit test (Kotlin)

Applies to an adapter that calls `CamundaClient` (or a project wrapper around it). Mock the client relaxed and verify the whole fluent command chain; no engine runs.

Contents: Message, start, signal: verify the chain · User task: stub the search, capture the filter · With a project wrapper

## Message, start, signal: verify the chain

```kotlin
import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientException
import io.miragon.blueprint.adapter.process.Messages

class LeasingProcessAdapterTest {

    private val camundaClient = mockk<CamundaClient>(relaxed = true)
    private val underTest = LeasingProcessAdapter(camundaClient)

    private val applicationId = ApplicationId.new()

    @Test
    fun `publishes contract signed with the application id as correlation key`() {
        underTest.notifyContractSigned(applicationId)

        verify {
            camundaClient.newPublishMessageCommand()
                .messageName(Messages.MIRAVELO_CONTRACT_SIGNED.value)
                .correlationKey(applicationId.value.toString())
                .timeToLive(Duration.ofSeconds(10))
                .send()
        }
    }

    @Test
    fun `lets an engine failure reach the caller`() {
        every {
            camundaClient.newPublishMessageCommand()
                .messageName(any()).correlationKey(any()).timeToLive(any())
                .send().join()
        } throws ClientException("gateway unavailable")

        assertThrows<ClientException> { underTest.notifyContractSigned(applicationId) }
    }
}
```

A relaxed chain is keyed by the arguments of every step, so a wrong name, key or variable map fails the `verify`. Use concrete values in every step and the call order of the adapter; an `any()` in the middle verifies a different child mock. The same shape covers `newCreateInstanceCommand().bpmnProcessId(PROCESS_ID.value).latestVersion().variables(…)` and `newBroadcastSignalCommand().signalName(Signals.X.value).variables(…)`.

## User task: stub the search, capture the filter

```kotlin
@Test
fun `completes the open clarification task of the application`() {
    val filter = slot<Consumer<UserTaskFilter>>()
    val task = mockk<UserTask> { every { userTaskKey } returns 42L }
    val variable = mockk<Variable> {
        every { name } returns FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value
        every { value } returns "\"${applicationId.value}\""
    }
    every { camundaClient.newUserTaskSearchRequest().filter(capture(filter)).send().join().items() } returns listOf(task)
    every { camundaClient.newUserTaskVariableSearchRequest(42L).send().join().items() } returns listOf(variable)

    underTest.completeAlternativeClarification(applicationId, alternativeFound = true)

    val appliedFilter = mockk<UserTaskFilter>(relaxed = true)
    filter.captured.accept(appliedFilter)
    verify {
        appliedFilter.state(UserTaskState.CREATED)
        appliedFilter.elementId(FlowNodes.UserTaskClarifyAlternative.id.value)
        camundaClient.newCompleteUserTaskCommand(42L)
            .variables(mapOf(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to true))
            .send()
    }
}

@Test
fun `fails when the application has no open clarification task`() {
    every {
        camundaClient.newUserTaskSearchRequest().filter(any<Consumer<UserTaskFilter>>()).send().join().items()
    } returns emptyList()

    assertThrows<NoSuchElementException> { underTest.completeAlternativeClarification(applicationId, true) }
}
```

Imports: `io.camunda.client.api.search.filter.UserTaskFilter`, `io.camunda.client.api.search.response.UserTask` / `Variable`, `io.camunda.client.api.search.enums.UserTaskState`, `java.util.function.Consumer`. Zeebe returns variable values as JSON, so the stubbed string value is quoted.

## With a project wrapper

If the adapter calls a wrapper such as `ProcessEngineApi`, mock the wrapper instead of the client:

```kotlin
private val engineApi = mockk<ProcessEngineApi>(relaxed = true)
private val camundaClient = mockk<CamundaClient>()
private val underTest = LeasingProcessAdapter(engineApi, camundaClient)

@Test
fun `sends contract signed with the application id as correlation key`() {
    val id = ApplicationId.new()

    underTest.correlateContractSigned(id)

    verify { engineApi.sendMessage(Messages.MIRAVELO_CONTRACT_SIGNED, id.value.toString(), emptyMap()) }
    confirmVerified(engineApi, camundaClient)
}
```

Expect the generated typed names (`Messages.X`, `FlowNodes.<Node>.Variables.X`) there, since the wrapper takes them unwrapped. Pass default arguments explicitly in `verify`.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/kotlin-gradle/service/common-zeebe/src/test/kotlin/io/miragon/common/zeebe/engine/ProcessEngineApiTest.kt
