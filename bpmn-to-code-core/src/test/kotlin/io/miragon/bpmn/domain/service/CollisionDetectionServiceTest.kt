package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.jobWorkerTask
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.testProcessModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CollisionDetectionServiceTest {

    private val underTest = CollisionDetectionService()

    @Test
    fun `findCollisions returns empty when no collisions exist`() {
        // given: a model with distinct constant names across all element types
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_Task1"),
                FlowNodeDefinition.Unknown(id = "Activity_Task2"),
                jobWorkerTask(id = "Task1", jobType = "newsletter.sendMail"),
                jobWorkerTask(id = "Task2", jobType = "newsletter.sendConfirmationMail"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "Message_FormSubmitted", name = "Message_FormSubmitted"),
                RootElementDefinition.Message(id = "Message_SubscriptionConfirmed", name = "Message_SubscriptionConfirmed"),
            ),
        )

        // when / then: no collisions are detected
        assertThat(underTest.findCollisions(model)).isEmpty()
    }

    @Test
    fun `findCollisions allows true duplicates with same original ID`() {
        // given: a model with exact duplicate elements (same id)
        val model = testProcessModel(
            processId = "TestProcess",
            messages = listOf(
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
                RootElementDefinition.Message(id = "Message_Test", name = "Message_Test"),
            ),
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_SendMail"),
                FlowNodeDefinition.Unknown(id = "Activity_SendMail"),
            ),
        )

        // when / then: true duplicates are not treated as collisions
        assertThat(underTest.findCollisions(model)).isEmpty()
    }

    @Test
    fun `findCollisions detects collision with case variation in FlowNodes`() {
        // given: two flow nodes that differ only in case
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "eventData"),
                FlowNodeDefinition.Unknown(id = "EventData"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision is reported with the expected constant name
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("EVENT_DATA")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("EventData", "eventData")
        assertThat(collisions[0].processId).isEqualTo("TestProcess")
    }

    @Test
    fun `findCollisions detects collision with separator variation in FlowNodes`() {
        // given: two flow nodes that differ only in separator character
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_dataProcessed"),
                FlowNodeDefinition.Unknown(id = "endEvent-dataProcessed"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("END_EVENT_DATA_PROCESSED")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder(
            "endEvent-dataProcessed",
            "endEvent_dataProcessed",
        )
    }

    @Test
    fun `findCollisions detects collision with mixed case and separator variation`() {
        // given: three flow nodes that all normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "eventData"),
                FlowNodeDefinition.Unknown(id = "event-data"),
                FlowNodeDefinition.Unknown(id = "event_Data"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision groups all three IDs
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("EVENT_DATA")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder(
            "event-data",
            "eventData",
            "event_Data",
        )
    }

    @Test
    fun `findCollisions detects folding collision that UPPER_SNAKE misses`() {
        // given: two flow nodes whose ids keep distinct constants (FOO, _FOO) but fold to the
        // same PascalCase object name (Foo) used for Variables/CallActivities objects
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "foo"),
                FlowNodeDefinition.Unknown(id = "-foo"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one collision is reported on the folded object-name basis
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("FlowNode")
        assertThat(collisions[0].constantName).isEqualTo("Foo")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("-foo", "foo")
    }

    @Test
    fun `findCollisions does not double-report a collision that surfaces on both bases`() {
        // given: two flow nodes that collide in UPPER_SNAKE and in PascalCase folding
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: exactly one collision, keeping the UPPER_SNAKE constant name
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].constantName).isEqualTo("END_EVENT_COMPLETE")
    }

    @Test
    fun `findCollisions detects UPPER_SNAKE collision that folding misses`() {
        // given: two flow nodes that share a constant (FOO_BAR) but keep distinct PascalCase names
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "fooBar"),
                FlowNodeDefinition.Unknown(id = "fooBAR"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: still reported once on the UPPER_SNAKE basis
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].constantName).isEqualTo("FOO_BAR")
        assertThat(collisions[0].conflictingIds).containsExactlyInAnyOrder("fooBAR", "fooBar")
    }

    @Test
    fun `findCollisions detects collisions in Messages`() {
        // given: two messages that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_formSubmitted"),
                RootElementDefinition.Message(id = "msg2", name = "message-formSubmitted"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one Message collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Message")
        assertThat(collisions[0].constantName).isEqualTo("MESSAGE_FORM_SUBMITTED")
    }

    @Test
    fun `findCollisions detects collisions in ServiceTasks`() {
        // given: two service tasks with implementations that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                jobWorkerTask(id = "task1", jobType = "newsletter.sendMail"),
                jobWorkerTask(id = "task2", jobType = "newsletter_sendMail"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one ServiceTask collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("ServiceTask")
        assertThat(collisions[0].constantName).isEqualTo("NEWSLETTER_SEND_MAIL")
    }

    @Test
    fun `findCollisions detects collisions in Signals`() {
        // given: two signals that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal.complete"),
                RootElementDefinition.Signal(id = "sig2", name = "signal_complete"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one Signal collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Signal")
        assertThat(collisions[0].constantName).isEqualTo("SIGNAL_COMPLETE")
    }

    @Test
    fun `findCollisions detects collisions in Errors`() {
        // given: two errors that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            errors = listOf(
                RootElementDefinition.Error(id = "err1", name = "Error_InvalidMail", code = "400"),
                RootElementDefinition.Error(id = "err2", name = "Error-InvalidMail", code = "400"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one Error collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Error")
        assertThat(collisions[0].constantName).isEqualTo("ERROR_INVALID_MAIL")
    }

    @Test
    fun `findCollisions detects collisions in Timers`() {
        // given: two timer event nodes whose ids normalize to the same constant. Timer definitions are
        // now keyed by their carrying node's id, so this necessarily surfaces a FlowNode collision too.
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Event(
                    id = "Duration",
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT1M")),
                ),
                FlowNodeDefinition.Event(
                    id = "duration",
                    shape = EventShape.INTERMEDIATE_CATCH_EVENT,
                    eventDefinitions = listOf(EventDefinitionInstance.Timer(TimerType.DURATION, "PT2M")),
                ),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: a Timer collision is reported on the shared constant
        val timerCollisions = collisions.filter { it.variableType == "Timer" }
        assertThat(timerCollisions).hasSize(1)
        assertThat(timerCollisions[0].constantName).isEqualTo("DURATION")
        assertThat(timerCollisions[0].conflictingIds).containsExactlyInAnyOrder("Duration", "duration")
    }

    @Test
    fun `findCollisions detects collisions in Variables`() {
        // given: two nodes with variables that normalize to the same constant
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(
                    id = "node1",
                    variables = listOf(VariableDefinition(name = "userId", direction = VariableDirection.INPUT)),
                ),
                FlowNodeDefinition.Unknown(
                    id = "node2",
                    variables = listOf(VariableDefinition(name = "user_id", direction = VariableDirection.INPUT)),
                ),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: one Variable collision is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].variableType).isEqualTo("Variable")
        assertThat(collisions[0].constantName).isEqualTo("USER_ID")
    }

    @Test
    fun `findCollisions detects multiple collisions across different variable types`() {
        // given: a model with collisions in FlowNodes, Messages, and Signals simultaneously
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
            messages = listOf(
                RootElementDefinition.Message(id = "msg1", name = "message_sent"),
                RootElementDefinition.Message(id = "msg2", name = "message-sent"),
            ),
            signals = listOf(
                RootElementDefinition.Signal(id = "sig1", name = "signal_ready"),
                RootElementDefinition.Signal(id = "sig2", name = "signal-ready"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: three collisions are detected, one per type
        assertThat(collisions).hasSize(3)
        assertThat(collisions.map { it.variableType }).containsExactlyInAnyOrder(
            "FlowNode",
            "Message",
            "Signal",
        )
    }

    @Test
    fun `findCollisions handles mixed valid and collision cases`() {
        // given: a model where most nodes are unique but two share a constant name
        val model = testProcessModel(
            processId = "TestProcess",
            flowNodes = listOf(
                FlowNodeDefinition.Unknown(id = "Activity_Task1"),
                FlowNodeDefinition.Unknown(id = "Activity_Task2"),
                FlowNodeDefinition.Unknown(id = "Activity_Task3"),
                FlowNodeDefinition.Unknown(id = "endEvent_complete"),
                FlowNodeDefinition.Unknown(id = "endEvent-complete"),
            ),
        )

        // when: checking for collisions
        val collisions = underTest.findCollisions(model)

        // then: only the colliding pair is reported
        assertThat(collisions).hasSize(1)
        assertThat(collisions[0].constantName).isEqualTo("END_EVENT_COMPLETE")
    }
}
