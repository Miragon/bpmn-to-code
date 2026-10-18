# Camunda 7 family, remote · process adapter test (Kotlin)

Applies to adapters that call the engine REST API through a generated client (`MessageApi`, `TaskApi`, `SignalApi` over a Spring `RestClient`). Bind `MockRestServiceServer` to the client's builder and assert the JSON the engine would receive; no engine runs. Imports: `MockRestRequestMatchers.*`, `MockRestResponseCreators.*`, `org.hamcrest.Matchers.containsString`, and the client package of your family (`org.cibseven.rest.client.api`, `org.operaton.rest.client.api`, …).

## Test

```kotlin
class BikeLeasingProcessAdapterTest {

    private val baseUrl = "http://engine/engine-rest"
    private val builder = RestClient.builder().baseUrl(baseUrl)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.build()
    private val underTest = BikeLeasingProcessAdapter(MessageApi(client), TaskApi(client))

    private val application = testLeasingApplication()
    private val businessKey = application.id.value.toString()

    @Test
    fun `startBikeLeasing sends the start message with business key and typed variables`() {
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        server.expect(requestTo("$baseUrl/message"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.messageName").value(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value))
            .andExpect(jsonPath("$.businessKey").value(businessKey))
            .andExpect(jsonPath("$.processVariables.${start.BIKE_ID.value}.value").value(application.bikeId.value))
            .andExpect(jsonPath("$.processVariables.${start.BIKE_ID.value}.type").value("String"))
            .andExpect(jsonPath("$.processVariables.${start.AGE.value}.value").value(application.age))
            .andExpect(jsonPath("$.processVariables.${start.AGE.value}.type").value("Integer"))
            .andRespond(withSuccess())

        underTest.startBikeLeasing(application)

        server.verify()
    }

    @Test
    fun `completeAlternativeClarification finds the task by business key and element id`() {
        val output = FlowNodes.UserTaskClarifyAlternative.Variables
        server.expect(requestTo(containsString("/task?")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("processInstanceBusinessKey", businessKey))
            .andExpect(queryParam("taskDefinitionKey", FlowNodes.UserTaskClarifyAlternative.id.value))
            .andRespond(withSuccess("""[{"id":"task-1"}]""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$baseUrl/task/task-1/complete"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.variables.${output.ALTERNATIVE_FOUND.value}.value").value(true))
            .andExpect(jsonPath("$.variables.${output.ALTERNATIVE_FOUND.value}.type").value("Boolean"))
            .andRespond(withSuccess())

        underTest.completeAlternativeClarification(application.id, alternativeFound = true)

        server.verify()
    }

    @Test
    fun `completeAlternativeClarification fails when no task is open`() {
        server.expect(requestTo(containsString("/task?")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        assertThatThrownBy { underTest.completeAlternativeClarification(application.id, alternativeFound = true) }
            .isInstanceOf(IllegalStateException::class.java)
        server.verify()
    }

    @Test
    fun `notifyContractSigned passes an engine rejection to the caller`() {
        server.expect(requestTo("$baseUrl/message"))
            .andExpect(jsonPath("$.messageName").value(Messages.MIRAVELO_CONTRACT_SIGNED.value))
            .andExpect(jsonPath("$.businessKey").value(businessKey))
            .andRespond(withBadRequest())

        assertThatThrownBy { underTest.notifyContractSigned(application.id) }
            .isInstanceOf(RestClientResponseException::class.java)
        server.verify()
    }
}
```

A signal is asserted the same way: `requestTo("$baseUrl/signal")`, `jsonPath("$.name").value(Signals.MIRAVELO_MEMBER_ACTIVATED.value)`, `jsonPath("$.variables.<name>.value")`.

## Notes

- `MockRestServiceServer` replaces MockK here: it checks the real wire format the generated client produces, which a mocked `MessageApi` would hide. `server.verify()` is the `confirmVerified` equivalent: it fails on a missing expected call, and an unexpected call fails immediately.
- Bind the server to the builder before `build()`, otherwise the client bypasses it.
- `"$.messageName"` needs no escaping in Kotlin; `$` before `.` is not a template.
- Assert `type` next to `value` for every variable. A wrong type passes a value-only check and only fails on the engine.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/kotlin-gradle/service/example-service/src/test/kotlin/io/miragon/blueprint/adapter/outbound/engine/RemoteLeasingProcessAdapterTest.kt
