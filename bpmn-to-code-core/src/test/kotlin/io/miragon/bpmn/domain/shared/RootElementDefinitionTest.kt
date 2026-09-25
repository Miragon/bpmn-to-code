package io.miragon.bpmn.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RootElementDefinitionTest {

    @Test
    fun `error name combines name and code`() {
        // given: an error with name and code
        val error = RootElementDefinition.Error(id = "Error_1", name = "InvalidMail", code = "500")

        // when / then: the constant name carries the code
        assertThat(error.getName()).isEqualTo("INVALID_MAIL_500")
    }

    @Test
    fun `error name without code is the name only`() {
        // given: an error without code
        val error = RootElementDefinition.Error(id = "Error_1", name = "InvalidMail", code = null)

        // when / then: the constant name is the name
        assertThat(error.getName()).isEqualTo("INVALID_MAIL")
    }

    @Test
    fun `error without name has an empty name`() {
        // given: an error with only a code
        val error = RootElementDefinition.Error(id = "Error_1", name = null, code = "500")

        // when / then: it yields no constant
        assertThat(error.getName()).isEmpty()
    }

    @Test
    fun `errors with same name and different code yield distinct names`() {
        // given: two errors sharing a name
        val first = RootElementDefinition.Error(id = "Error_1", name = "InvalidMail", code = "500")
        val second = RootElementDefinition.Error(id = "Error_2", name = "InvalidMail", code = "400")

        // when / then: both keep their own constant
        assertThat(first.getName()).isNotEqualTo(second.getName())
    }

    @Test
    fun `escalation name combines name and code`() {
        // given: an escalation with name and code
        val escalation = RootElementDefinition.Escalation(id = "Escalation_1", name = "LateDelivery", code = "LATE")

        // when / then: the constant name carries the code
        assertThat(escalation.getName()).isEqualTo("LATE_DELIVERY_LATE")
    }
}
