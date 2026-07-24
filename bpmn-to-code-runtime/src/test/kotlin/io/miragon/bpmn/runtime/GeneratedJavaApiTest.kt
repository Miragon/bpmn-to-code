package io.miragon.bpmn.runtime

import io.miragon.bpmn.runtime.example.NewsletterSubscriptionProcessApi.Relations
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Exercises the *generated Java* navigation API (compiled here as a Java test source) so the Java code path —
 * `extends AbstractFlowNode implements Navigable<Next>`, the `super(...)` constructor, and the nested `Next`
 * `then()` accessors — is compiled and executed, not just parsed. The Kotlin generator output is covered by
 * `ProcessPathIntegrationTest`; nothing else compiles generated Java against the runtime interfaces.
 */
class GeneratedJavaApiTest {

    @Test
    fun `generated java nodes carry FlowNode metadata`() {
        val start = Relations.then().startEventSubmitRegistrationForm()
        assertThat(start.id.value).isEqualTo("StartEvent_SubmitRegistrationForm")
        assertThat(start.elementType).isEqualTo("MESSAGE_START_EVENT")
    }

    @Test
    fun `generated java then navigates to a real successor`() {
        val counter = Relations.then()
            .startEventSubmitRegistrationForm()
            .then()
            .serviceTaskIncrementSubscriptionCounter()
        assertThat(counter.id.value).isEqualTo("serviceTask_incrementSubscriptionCounter")
        assertThat(counter.elementType).isEqualTo("SERVICE_TASK")
    }

    @Test
    fun `generated java subprocess exposes its interior via inner`() {
        val innerStart = Relations.subProcessConfirmation()
            .inner()
            .then()
            .startEventRequestReceived()
        assertThat(innerStart.id.value).isEqualTo("StartEvent_RequestReceived")
    }
}
