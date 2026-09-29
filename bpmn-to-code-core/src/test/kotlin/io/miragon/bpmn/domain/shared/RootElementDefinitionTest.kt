package io.miragon.bpmn.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RootElementDefinitionTest {

    @Test
    fun `error name is the name without the code`() {
        // given: an error with name and code
        val error = RootElementDefinition.Error(id = "Error_1", name = "InvalidMail", code = "500")

        // when / then: the constant name omits the code
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
    fun `escalation name is the name without the code`() {
        // given: an escalation with name and code
        val escalation = RootElementDefinition.Escalation(id = "Escalation_1", name = "LateDelivery", code = "LATE")

        // when / then: the constant name omits the code
        assertThat(escalation.getName()).isEqualTo("LATE_DELIVERY")
    }

    @Test
    fun `names drop a leading prefix matching their kind`() {
        // given: one definition of each kind, prefixed with its kind
        val message = RootElementDefinition.Message(id = "Message_1", name = "Message_FormSubmitted")
        val signal = RootElementDefinition.Signal(id = "Signal_1", name = "signalShutdown")
        val error = RootElementDefinition.Error(id = "Error_1", name = "ERROR_INVALID_MAIL", code = "500")
        val escalation = RootElementDefinition.Escalation(id = "Escalation_1", name = "Escalation-LateDelivery", code = "LATE")

        // when / then: the prefix is stripped
        assertThat(message.getName()).isEqualTo("FORM_SUBMITTED")
        assertThat(signal.getName()).isEqualTo("SHUTDOWN")
        assertThat(error.getName()).isEqualTo("INVALID_MAIL")
        assertThat(escalation.getName()).isEqualTo("LATE_DELIVERY")
    }

    @Test
    fun `names keep the kind when nothing valid would remain`() {
        // given: names that consist of the kind only, or would start with a digit
        val kindOnly = RootElementDefinition.Message(id = "Message_1", name = "Message")
        val digitRemainder = RootElementDefinition.Signal(id = "Signal_1", name = "Signal_1")

        // when / then: the name stays unchanged
        assertThat(kindOnly.getName()).isEqualTo("MESSAGE")
        assertThat(digitRemainder.getName()).isEqualTo("SIGNAL_1")
    }

    @Test
    fun `names keep a prefix of another kind`() {
        // given: a message named like a signal
        val message = RootElementDefinition.Message(id = "Message_1", name = "SignalReceived")

        // when / then: only the own kind is stripped
        assertThat(message.getName()).isEqualTo("SIGNAL_RECEIVED")
    }

    @Test
    fun `message raw name stays the full name`() {
        // given: a message prefixed with its kind
        val message = RootElementDefinition.Message(id = "Message_1", name = "Message_FormSubmitted")

        // when / then: the raw name still is the correlation name
        assertThat(message.getRawName()).isEqualTo("Message_FormSubmitted")
    }
}
