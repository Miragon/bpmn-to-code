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

class UncaughtSignalThrowRuleTest {

    private val underTest = UncaughtSignalThrowRule()

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
    fun `warns on a thrown signal that is never caught in the fileset`() {

        // given: a single model that throws a signal no one subscribes to
        val thrower = model("registration", throwNode("throw1", "RegistrationBlocked"))

        // when: the rule validates the model
        val violations = validate(thrower)

        // then: a single WARN naming the uncaught signal and its throwing element
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.WARN)
        assertThat(violations[0].elementId).isEqualTo("throw1")
        assertThat(violations[0].processId).isEqualTo("registration")
        assertThat(violations[0].message).contains("RegistrationBlocked")
    }

    @Test
    fun `no warning when a catcher exists in the same model`() {

        // given: the throw and a matching catch live in one model
        val model = model("registration", throwNode("throw1", "RegistrationBlocked"), catchNode("catch1", "RegistrationBlocked"))

        // when: the rule validates the model
        val violations = validate(model)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no warning when the catcher lives in another loaded model`() {

        // given: the signal is thrown in one process and caught in another - the cross-model case
        val thrower = model("registration", throwNode("throw1", "RegistrationBlocked"))
        val catcher = model("monitoring", catchNode("catch1", "RegistrationBlocked"))

        // when: the rule validates both models together
        val violations = validate(thrower, catcher)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `warns only for the signal whose catcher is missing`() {

        // given: two thrown signals, only one of which is caught anywhere
        val thrower = model("registration", throwNode("throw1", "RegistrationBlocked"), throwNode("throw2", "AccountLocked"))
        val catcher = model("monitoring", catchNode("catch1", "RegistrationBlocked"))

        // when: the rule validates both models together
        val violations = validate(thrower, catcher)

        // then: only the uncaught signal is reported
        assertThat(violations).hasSize(1)
        assertThat(violations[0].elementId).isEqualTo("throw2")
        assertThat(violations[0].message).contains("AccountLocked")
    }
}
