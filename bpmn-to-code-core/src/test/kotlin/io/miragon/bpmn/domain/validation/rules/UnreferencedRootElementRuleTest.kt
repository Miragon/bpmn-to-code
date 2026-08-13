package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.testProcessModel
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnreferencedRootElementRuleTest {

    private val underTest = UnreferencedRootElementRule()

    private fun messageStartEvent(messageRef: String) = FlowNodeDefinition.Event(
        id = "StartEvent_Received",
        shape = EventShape.START_EVENT,
        eventDefinitions = listOf(EventDefinitionInstance.Message(MessageReference(messageRef, "used"))),
    )

    @Test
    fun `warns about a message no element references`() {

        // given: two declared messages, only one of which an event points at
        val model = testProcessModel(
            flowNodes = listOf(messageStartEvent("Message_Used")),
            messages = listOf(
                RootElementDefinition.Message(id = "Message_Used", name = "used"),
                RootElementDefinition.Message(id = "Message_Orphan", name = "orphan"),
            ),
            signals = emptyList(),
            errors = emptyList(),
        )

        // when
        val violations = underTest.validate(SingleModelValidationContext(model, ProcessEngine.ZEEBE))

        // then: the leftover declaration is named, the used one is not
        assertThat(violations).hasSize(1)
        assertThat(violations.single().elementId).isEqualTo("Message_Orphan")
        assertThat(violations.single().severity).isEqualTo(Severity.WARN)
        assertThat(violations.single().message).contains("no element references it")
    }

    @Test
    fun `reports nothing when every root element is referenced`() {

        // given
        val model = testProcessModel(
            flowNodes = listOf(messageStartEvent("Message_Used")),
            messages = listOf(RootElementDefinition.Message(id = "Message_Used", name = "used")),
            signals = emptyList(),
            errors = emptyList(),
        )

        // when / then
        assertThat(underTest.validate(SingleModelValidationContext(model, ProcessEngine.ZEEBE))).isEmpty()
    }

    @Test
    fun `covers signals and errors as well as messages`() {

        // given: an unreferenced entry in each registry
        val model = testProcessModel(
            flowNodes = listOf(FlowNodeDefinition.Unknown(id = "node")),
            messages = listOf(RootElementDefinition.Message(id = "Message_Orphan", name = "m")),
            signals = listOf(RootElementDefinition.Signal(id = "Signal_Orphan", name = "s")),
            errors = listOf(RootElementDefinition.Error(id = "Error_Orphan", name = "e", code = "500")),
        )

        // when
        val violations = underTest.validate(SingleModelValidationContext(model, ProcessEngine.ZEEBE))

        // then
        assertThat(violations.map { it.elementId })
            .containsExactlyInAnyOrder("Message_Orphan", "Signal_Orphan", "Error_Orphan")
    }

    @Test
    fun `counts a message referenced by a receive task as used`() {

        // given: send and receive tasks reference their message directly, not through an event definition
        val receiveTask = FlowNodeDefinition.Activity.Task(
            id = "Activity_Await",
            kind = TaskKind.RECEIVE,
            message = MessageReference("Message_Used", "used"),
        )
        val model = testProcessModel(
            flowNodes = listOf(receiveTask),
            messages = listOf(RootElementDefinition.Message(id = "Message_Used", name = "used")),
            signals = emptyList(),
            errors = emptyList(),
        )

        // when / then
        assertThat(underTest.validate(SingleModelValidationContext(model, ProcessEngine.ZEEBE))).isEmpty()
    }
}
