package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProcessApiNamesTest {

    private val underTest = ProcessApiNames

    @Test
    fun `names an element after its id in PascalCase`() {
        assertThat(underTest.elementName(elementId = "serviceTask_sendContract")).isEqualTo("ServiceTaskSendContract")
    }

    @ParameterizedTest
    @ValueSource(strings = ["FlowNodes", "Next", "Messages", "HashCode", "Id"])
    fun `reserves the names the Process API declares itself`(elementName: String) {
        assertThat(underTest.isReserved(elementName)).isTrue()
    }

    @Test
    fun `keeps any other name free`() {
        assertThat(underTest.isReserved(elementName = "ServiceTaskSendContract")).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["newsletterSubscription", "newsletter-subscription", "newsletter_subscription"])
    fun `names the Process API in PascalCase regardless of the separators of the process id`(processId: String) {
        val model = testProcessModel(processId = processId)

        assertThat(underTest.apiTypeName(model)).isEqualTo("NewsletterSubscriptionProcessApi")
    }

    @Test
    fun `leads the name of the Process API with the variant name`() {
        val model = testProcessModel(processId = "newsletter-subscription", variantName = "corporate")

        assertThat(underTest.apiTypeName(model)).isEqualTo("CorporateNewsletterSubscriptionProcessApi")
    }

    @Test
    fun `reads the separators of a variant name like those of the process id`() {
        val model = testProcessModel(processId = "newsletterSubscription", variantName = "corporate-fleet")

        assertThat(underTest.apiTypeName(model)).isEqualTo("CorporateFleetNewsletterSubscriptionProcessApi")
    }
}
