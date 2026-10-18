# Camunda 7 family, remote · process adapter test (Java)

Applies to adapters that call the engine REST API through a generated client (`MessageApi`, `TaskApi`, `SignalApi` on an `ApiClient` over a Spring `RestClient`). Bind `MockRestServiceServer` to the client's builder and assert the JSON the engine would receive; no engine runs. Static imports: `MockRestRequestMatchers.*`, `MockRestResponseCreators.*`; client classes from your family's package (`org.cibseven.rest.client`, `org.operaton.rest.client`, …).

## Test

```java
class BikeLeasingProcessAdapterTest {

    private static final String BASE_URL = "http://engine/engine-rest";

    private final LeasingApplication application = testLeasingApplication();
    private final String businessKey = application.id().value().toString();
    private MockRestServiceServer server;
    private BikeLeasingProcessAdapter underTest;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        ApiClient client = new ApiClient(builder.build()).setBasePath(BASE_URL);
        underTest = new BikeLeasingProcessAdapter(new MessageApi(client), new TaskApi(client));
    }

    @Test
    @DisplayName("startBikeLeasing sends the start message with business key and typed variables")
    void startSendsTheStartMessage() {
        String bikeId = "$.processVariables." + FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue();
        String age = "$.processVariables." + FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue();
        server.expect(requestTo(BASE_URL + "/message"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messageName").value(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue()))
                .andExpect(jsonPath("$.businessKey").value(businessKey))
                .andExpect(jsonPath(bikeId + ".value").value(application.bikeId().value()))
                .andExpect(jsonPath(bikeId + ".type").value("String"))
                .andExpect(jsonPath(age + ".value").value(application.age()))
                .andExpect(jsonPath(age + ".type").value("Integer"))
                .andRespond(withSuccess());

        underTest.startBikeLeasing(application);

        server.verify();
    }

    @Test
    @DisplayName("completeAlternativeClarification finds the task by business key and element id")
    void completeAlternativeClarificationFindsTheTask() {
        String alternativeFound = "$.variables." + FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue();
        server.expect(requestTo(containsString("/task?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("processInstanceBusinessKey", businessKey))
                .andExpect(queryParam("taskDefinitionKey", FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID))
                .andRespond(withSuccess("[{\"id\":\"task-1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/task/task-1/complete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath(alternativeFound + ".value").value(true))
                .andExpect(jsonPath(alternativeFound + ".type").value("Boolean"))
                .andRespond(withSuccess());

        underTest.completeAlternativeClarification(application.id(), true);

        server.verify();
    }

    @Test
    @DisplayName("completeAlternativeClarification fails when no task is open")
    void completeAlternativeClarificationFailsWithoutTask() {
        server.expect(requestTo(containsString("/task?"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> underTest.completeAlternativeClarification(application.id(), true))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    @DisplayName("notifyContractSigned passes an engine rejection to the caller")
    void notifyContractSignedPassesEngineRejection() {
        server.expect(requestTo(BASE_URL + "/message"))
                .andExpect(jsonPath("$.messageName").value(Messages.MIRAVELO_CONTRACT_SIGNED.getValue()))
                .andExpect(jsonPath("$.businessKey").value(businessKey))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> underTest.notifyContractSigned(application.id()))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }
}
```

A signal is asserted the same way: `requestTo(BASE_URL + "/signal")`, `jsonPath("$.name").value(Signals.MIRAVELO_MEMBER_ACTIVATED.getValue())`, `jsonPath("$.variables.<name>.value")`.

## Notes

- `MockRestServiceServer` replaces Mockito here: it checks the real wire format the generated client produces, which a mocked `MessageApi` would hide. `server.verify()` is the `verifyNoMoreInteractions` equivalent: it fails on a missing expected call, and an unexpected call fails immediately.
- Bind the server to the builder before `build()`, and give the `ApiClient` the same base path, otherwise the client bypasses the server or calls the generator's default URL.
- Assert `type` next to `value` for every variable. A wrong type passes a value-only check and only fails on the engine.

Reference: https://github.com/miragon-blueprints/cibseven-remote-example/blob/main/java-maven/service/example-service/src/test/java/io/miragon/blueprint/adapter/outbound/engine/RemoteLeasingProcessAdapterTest.java
