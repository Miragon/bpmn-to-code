package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
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
    fun `prefixes the concrete event definition onto the shape, shape-only for terminate, conditional and link`() {
        // Only timer/message/error/signal/escalation/compensation surface as a prefix; conditional, link and
        // terminate render shape-only in the flat Process API vocabulary.
        val cases: List<Pair<EventDefinitionInstance, String>> = listOf(
            EventDefinitionInstance.Timer() to "TIMER_BOUNDARY_EVENT",
            EventDefinitionInstance.Message(MessageReference("m", "m")) to "MESSAGE_BOUNDARY_EVENT",
            EventDefinitionInstance.Error("e", "e", "1") to "ERROR_BOUNDARY_EVENT",
            EventDefinitionInstance.Signal("s", "s") to "SIGNAL_BOUNDARY_EVENT",
            EventDefinitionInstance.Escalation("esc", "esc", "2") to "ESCALATION_BOUNDARY_EVENT",
            EventDefinitionInstance.Compensation() to "COMPENSATION_BOUNDARY_EVENT",
            EventDefinitionInstance.Conditional("=x") to "BOUNDARY_EVENT",
            EventDefinitionInstance.Link("link") to "BOUNDARY_EVENT",
            EventDefinitionInstance.Terminate to "BOUNDARY_EVENT",
        )
        cases.forEach { (definition, expectedName) ->
            val event = FlowNodeDefinition.Event(
                id = "event",
                shape = EventShape.BOUNDARY_EVENT,
                eventDefinitions = listOf(definition),
            )
            assertThat(ElementTypeName.of(event)).isEqualTo(expectedName)
        }
        // every event-definition kind is exercised exactly once
        assertThat(cases.map { it.first.type })
            .containsExactlyInAnyOrder(*EventDefinitionInstance.Type.entries.toTypedArray())
    }

    @Test
    fun `renders a terminate end event shape-only`() {
        val terminateEnd = FlowNodeDefinition.Event(
            id = "end",
            shape = EventShape.END_EVENT,
            eventDefinitions = listOf(EventDefinitionInstance.Terminate),
        )
        assertThat(ElementTypeName.of(terminateEnd)).isEqualTo("END_EVENT")
    }

    @Test
    fun `selects the first prefixed event definition when several are present`() {
        val event = FlowNodeDefinition.Event(
            id = "event",
            shape = EventShape.BOUNDARY_EVENT,
            eventDefinitions = listOf(
                EventDefinitionInstance.Link("link"),
                EventDefinitionInstance.Error("e", "e", "1"),
                EventDefinitionInstance.Timer(),
            ),
        )
        assertThat(ElementTypeName.of(event)).isEqualTo("ERROR_BOUNDARY_EVENT")
    }

    @Test
    fun `maps unknown to its element-type string`() {
        assertThat(ElementTypeName.of(FlowNodeDefinition.Unknown(id = "unknown"))).isEqualTo("UNKNOWN")
    }
}
