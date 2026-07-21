package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDirection
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.testBpmnModel
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UncaughtMessageThrowRuleTest {

    private val underTest = UncaughtMessageThrowRule()

    private fun throwNode(id: String, message: String) = FlowNodeDefinition(
        id = id,
        properties = FlowNodeProperties.MessageEvent(message, MessageDirection.THROW),
    )

    private fun catchNode(id: String, message: String) = FlowNodeDefinition(
        id = id,
        properties = FlowNodeProperties.MessageEvent(message, MessageDirection.CATCH),
    )

    private fun model(processId: String, vararg nodes: FlowNodeDefinition) =
        testBpmnModel(processId = processId, flowNodes = nodes.toList())

    private fun validate(vararg models: io.miragon.bpmn.domain.BpmnModel) =
        underTest.validate(CrossModelValidationContext(models = models.toList(), engine = ProcessEngine.ZEEBE))

    @Test
    fun `warns on a thrown message that is never caught in the fileset`() {

        // given: a single model that throws a message no one catches
        val thrower = model("orderPlacement", throwNode("throw1", "OrderShipped"))

        // when: the rule validates the model
        val violations = validate(thrower)

        // then: a single WARN naming the uncaught message and its throwing element
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.WARN)
        assertThat(violations[0].elementId).isEqualTo("throw1")
        assertThat(violations[0].processId).isEqualTo("orderPlacement")
        assertThat(violations[0].message).contains("OrderShipped")
    }

    @Test
    fun `no warning when a catcher exists in the same model`() {

        // given: the throw and a matching catch live in one model
        val model = model("orderPlacement", throwNode("throw1", "OrderShipped"), catchNode("catch1", "OrderShipped"))

        // when: the rule validates the model
        val violations = validate(model)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `no warning when the catcher lives in another loaded model`() {

        // given: the message is thrown in one process and caught in another - the cross-model case
        val thrower = model("orderPlacement", throwNode("throw1", "OrderShipped"))
        val catcher = model("shipping", catchNode("catch1", "OrderShipped"))

        // when: the rule validates both models together
        val violations = validate(thrower, catcher)

        // then: no violation is reported
        assertThat(violations).isEmpty()
    }

    @Test
    fun `warns only for the message whose catcher is missing`() {

        // given: two thrown messages, only one of which is caught anywhere
        val thrower = model("orderPlacement", throwNode("throw1", "OrderShipped"), throwNode("throw2", "OrderCancelled"))
        val catcher = model("shipping", catchNode("catch1", "OrderShipped"))

        // when: the rule validates both models together
        val violations = validate(thrower, catcher)

        // then: only the uncaught message is reported
        assertThat(violations).hasSize(1)
        assertThat(violations[0].elementId).isEqualTo("throw2")
        assertThat(violations[0].message).contains("OrderCancelled")
    }
}
