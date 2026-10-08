package io.miragon.bpmn.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnModelApiTest {

    private val processIds = listOf("newsletterSubscription", "newsletter-subscription", "newsletter_subscription")

    @Test
    fun `fileName returns PascalCase class name regardless of ID separator style`() {
        // given: the expected file name
        val expectedFileName = "NewsletterSubscriptionProcessApi"

        // when / then: all separator variants produce the same file name
        processIds.forEach { id ->
            val model = testProcessModel(processId = id)
            val api = testProcessModelApi(model = model)
            assertThat(api.fileName()).isEqualTo(expectedFileName)
        }
    }

    @Test
    fun `fileName leads the class name with the variant name`() {
        // given
        val model = testProcessModel(processId = "newsletter-subscription", variantName = "corporate")

        // when / then
        assertThat(testProcessModelApi(model = model).fileName()).isEqualTo("CorporateNewsletterSubscriptionProcessApi")
    }

    @Test
    fun `fileName reads the separators of a variant name like those of the process id`() {
        // given
        val model = testProcessModel(processId = "newsletterSubscription", variantName = "corporate-fleet")

        // when / then
        assertThat(testProcessModelApi(model = model).fileName()).isEqualTo("CorporateFleetNewsletterSubscriptionProcessApi")
    }
}
