package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingMessageNameRuleTest {

    private val underTest = MissingMessageNameRule()

    private fun validate(node: FlowNodeDefinition) =
        underTest.validate(SingleModelValidationContext(model = testProcessModel(flowNodes = listOf(node)), engine = ProcessEngine.ZEEBE))

    @Test
    fun `reports error for a message event whose message has no name`() {

        // given: a message catch event whose message reference carries no name
        val node = FlowNodeDefinition.Event(
            id = "msgEvent1",
            shape = EventShape.INTERMEDIATE_CATCH_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference(messageRef = "msg1", messageName = null))),
        )

        // when / then: an ERROR violation is reported for the event node
        val violations = validate(node)
        assertThat(violations).hasSize(1)
        assertThat(violations[0].severity).isEqualTo(Severity.ERROR)
        assertThat(violations[0].elementId).isEqualTo("msgEvent1")
    }

    @Test
    fun `no violations for a message event with a valid name`() {

        // given: a message catch event whose message reference has a name
        val node = FlowNodeDefinition.Event(
            id = "msgEvent1",
            shape = EventShape.INTERMEDIATE_CATCH_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference(messageRef = "msg1", messageName = "MyMessage"))),
        )

        // when / then: no violations
        assertThat(validate(node)).isEmpty()
    }

    @Test
    fun `does not flag a message throw event that carries no message reference`() {

        // given: a Zeebe message end event that publishes via a job worker, with an empty message reference
        val node = FlowNodeDefinition.Event(
            id = "msgThrow1",
            shape = EventShape.END_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference())),
        )

        // when / then: not flagged - there is no referenced message to be missing a name
        assertThat(validate(node)).isEmpty()
    }

    @Test
    fun `reports error for a send task whose message has no name`() {

        // given: a send task that references a message with no name
        val node = FlowNodeDefinition.Activity.Task(
            id = "send1",
            kind = TaskKind.SEND,
            message = MessageReference(messageRef = "msg1", messageName = null),
        )

        // when / then: an ERROR violation is reported for the task
        val violations = validate(node)
        assertThat(violations).hasSize(1)
        assertThat(violations[0].elementId).isEqualTo("send1")
    }

    @Test
    fun `does not flag a send task with no message`() {

        // given: a send task that carries no message at all - not this rule's concern
        val node = FlowNodeDefinition.Activity.Task(id = "send1", kind = TaskKind.SEND, message = null)

        // when / then: not flagged
        assertThat(validate(node)).isEmpty()
    }
}
