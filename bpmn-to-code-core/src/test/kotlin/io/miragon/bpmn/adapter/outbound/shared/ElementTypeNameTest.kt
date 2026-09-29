package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.runtime.BpmnElementType
import io.miragon.bpmn.runtime.BpmnEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ElementTypeNameTest {

    @Test
    fun `maps every task kind to its element-type string`() {
        val expected = mapOf(
            TaskKind.SERVICE to "SERVICE_TASK",
            TaskKind.USER to "USER_TASK",
            TaskKind.RECEIVE to "RECEIVE_TASK",
            TaskKind.SEND to "SEND_TASK",
            TaskKind.SCRIPT to "SCRIPT_TASK",
            TaskKind.MANUAL to "MANUAL_TASK",
            TaskKind.BUSINESS_RULE to "BUSINESS_RULE_TASK",
            TaskKind.NONE to "TASK",
        )
        expected.forEach { (kind, expectedName) ->
            assertThat(ElementTypeName.of(FlowNodeDefinition.Activity.Task(id = "task", kind = kind)))
                .isEqualTo(expectedName)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*TaskKind.entries.toTypedArray())
    }

    @Test
    fun `maps every gateway kind to its element-type string`() {
        val expected = mapOf(
            GatewayKind.EXCLUSIVE to "EXCLUSIVE_GATEWAY",
            GatewayKind.PARALLEL to "PARALLEL_GATEWAY",
            GatewayKind.INCLUSIVE to "INCLUSIVE_GATEWAY",
            GatewayKind.EVENT_BASED to "EVENT_BASED_GATEWAY",
            GatewayKind.COMPLEX to "COMPLEX_GATEWAY",
        )
        expected.forEach { (kind, expectedName) ->
            assertThat(ElementTypeName.of(FlowNodeDefinition.Gateway(id = "gw", kind = kind)))
                .isEqualTo(expectedName)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*GatewayKind.entries.toTypedArray())
    }

    @Test
    fun `maps every subprocess kind to its element-type string`() {
        val expected = mapOf(
            SubProcessKind.PLAIN to "SUB_PROCESS",
            SubProcessKind.EVENT to "EVENT_SUB_PROCESS",
            SubProcessKind.TRANSACTION to "TRANSACTION",
        )
        expected.forEach { (kind, expectedName) ->
            assertThat(ElementTypeName.of(FlowNodeDefinition.Activity.SubProcess(id = "sub", kind = kind)))
                .isEqualTo(expectedName)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*SubProcessKind.entries.toTypedArray())
    }

    @Test
    fun `maps call activity to its element-type string`() {
        val callActivity = FlowNodeDefinition.Activity.CallActivity(
            id = "call",
            definition = CallActivityDefinition("call", "called-process"),
        )
        assertThat(ElementTypeName.of(callActivity)).isEqualTo("CALL_ACTIVITY")
    }

    @Test
    fun `maps every event shape to its element-type string`() {
        val expected = mapOf(
            EventShape.START_EVENT to "START_EVENT",
            EventShape.END_EVENT to "END_EVENT",
            EventShape.INTERMEDIATE_CATCH_EVENT to "INTERMEDIATE_CATCH_EVENT",
            EventShape.INTERMEDIATE_THROW_EVENT to "INTERMEDIATE_THROW_EVENT",
            EventShape.BOUNDARY_EVENT to "BOUNDARY_EVENT",
        )
        expected.forEach { (shape, expectedName) ->
            assertThat(ElementTypeName.of(FlowNodeDefinition.Event(id = "event", shape = shape)))
                .isEqualTo(expectedName)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*EventShape.entries.toTypedArray())
    }

    @Test
    fun `renders an event shape-only, whatever its definition`() {
        val timerBoundary = FlowNodeDefinition.Event(
            id = "event",
            shape = EventShape.BOUNDARY_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Timer()),
        )
        assertThat(ElementTypeName.of(timerBoundary)).isEqualTo("BOUNDARY_EVENT")
    }

    @Test
    fun `maps every event definition kind to its event type`() {
        val definitions = allEventDefinitionKinds()
        definitions.forEach { definition ->
            val event = FlowNodeDefinition.Event(id = "event", shape = EventShape.END_EVENT, eventDefinitions = listOf(definition))
            assertThat(ElementTypeName.eventTypeOf(event)).isEqualTo(definition.type.name)
        }
        assertThat(definitions.map { it.type }).containsExactlyInAnyOrder(*EventDefinitionInstance.Type.entries.toTypedArray())
    }

    @Test
    fun `maps an event without definition to NONE and one with several to MULTIPLE`() {
        val none = FlowNodeDefinition.Event(id = "start", shape = EventShape.START_EVENT)
        val multiple = FlowNodeDefinition.Event(
            id = "start",
            shape = EventShape.START_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Timer(), EventDefinitionInstance.Signal()),
        )
        assertThat(ElementTypeName.eventTypeOf(none)).isEqualTo("NONE")
        assertThat(ElementTypeName.eventTypeOf(multiple)).isEqualTo("MULTIPLE")
    }

    @Test
    fun `maps unknown to its element-type string`() {
        assertThat(ElementTypeName.of(FlowNodeDefinition.Unknown(id = "unknown"))).isEqualTo("UNKNOWN")
    }

    @Test
    fun `renderable element types are exactly the runtime BpmnElementType constants`() {
        val nodes = EventShape.entries.map { FlowNodeDefinition.Event(id = "event", shape = it) } +
            TaskKind.entries.map { FlowNodeDefinition.Activity.Task(id = "task", kind = it) } +
            GatewayKind.entries.map { FlowNodeDefinition.Gateway(id = "gw", kind = it) } +
            SubProcessKind.entries.map { FlowNodeDefinition.Activity.SubProcess(id = "sub", kind = it) } +
            FlowNodeDefinition.Activity.CallActivity(id = "call", definition = CallActivityDefinition("call", "called")) +
            FlowNodeDefinition.Unknown(id = "unknown")

        assertThat(nodes.map { ElementTypeName.of(it) })
            .containsExactlyInAnyOrderElementsOf(BpmnElementType.entries.map { it.name })
    }

    @Test
    fun `renderable event types are exactly the runtime BpmnEventType constants`() {
        val definitionLists = listOf(emptyList<EventDefinitionInstance>(), allEventDefinitionKinds().take(2)) +
            allEventDefinitionKinds().map { listOf(it) }
        val events = definitionLists.map { FlowNodeDefinition.Event(id = "event", shape = EventShape.END_EVENT, eventDefinitions = it) }

        assertThat(events.map { ElementTypeName.eventTypeOf(it) })
            .containsExactlyInAnyOrderElementsOf(BpmnEventType.entries.map { it.name })
    }

    private fun allEventDefinitionKinds(): List<EventDefinitionInstance> = listOf(
        EventDefinitionInstance.Timer(),
        EventDefinitionInstance.Message(MessageReference("m", "m")),
        EventDefinitionInstance.Error(errorRef = "e", errorName = "e", errorCode = "1"),
        EventDefinitionInstance.Signal(signalRef = "s", signalName = "s"),
        EventDefinitionInstance.Escalation(escalationRef = "esc", escalationName = "esc", escalationCode = "2"),
        EventDefinitionInstance.Compensation(),
        EventDefinitionInstance.Conditional("=x"),
        EventDefinitionInstance.Link("link"),
        EventDefinitionInstance.Terminate,
    )
}
