package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBpmnModel
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnpublishedSignalCatchRuleTest {

    private val underTest = UnpublishedSignalCatchRule()

    private fun throwNode(id: String, signal: String) = FlowNodeDefinition(
        id = id,
        properties = FlowNodeProperties.SignalEvent(signal, EventDirection.THROW),
    )

    private fun catchNode(id: String, signal: String) = FlowNodeDefinition(
        id = id,
        properties = FlowNodeProperties.SignalEvent(signal, EventDirection.CATCH),
    )

    private fun model(processId: String, vararg nodes: FlowNodeDefinition) =
        testBpmnModel(processId = processId, flowNodes = nodes.toList())

    private fun validate(vararg models: io.miragon.bpmn.domain.BpmnModel) =
        underTest.validate(CrossModelValidationContext(models = models.toList(), engine = ProcessEngine.ZEEBE))

    @Test
    fun `warns on a caught signal that is never thrown in the fileset`() {

        // given: a single model that subscribes to a signal no one publishes
        val subscriber = model("monitoring", catchNode("catch1", "RegistrationBlocked"))

        // when: the rule validates the model
        val violations = validate(subscriber)

        // then: a single WARN naming the orphaned signal and its catching element
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.WARN)
        assertThat(violations[0].elementId).isEqualTo("catch1")
        assertThat(violations[0].processId).isEqualTo("monitoring")
        assertThat(violations[0].message).contains("RegistrationBlocked")
    }

    @Test
    fun `no warning when a thrower exists in the same model`() {

        // given: the catch and a matching throw live in one model
        val model = model("monitoring", catchNode("catch1", "RegistrationBlocked"), throwNode("throw1", "RegistrationBlocked"))

        // when: the rule validates the model
        val violations = validate(model)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no warning when the thrower lives in another loaded model`() {

        // given: the signal is caught in one process and thrown in another - the cross-model case
        val subscriber = model("monitoring", catchNode("catch1", "RegistrationBlocked"))
        val publisher = model("registration", throwNode("throw1", "RegistrationBlocked"))

        // when: the rule validates both models together
        val violations = validate(subscriber, publisher)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `warns only for the signal whose thrower is missing`() {

        // given: two caught signals, only one of which is thrown anywhere
        val subscriber = model("monitoring", catchNode("catch1", "RegistrationBlocked"), catchNode("catch2", "AccountLocked"))
        val publisher = model("registration", throwNode("throw1", "RegistrationBlocked"))

        // when: the rule validates both models together
        val violations = validate(subscriber, publisher)

        // then: only the orphaned signal is reported
        assertThat(violations).hasSize(1)
        assertThat(violations[0].elementId).isEqualTo("catch2")
        assertThat(violations[0].message).contains("AccountLocked")
    }
}
