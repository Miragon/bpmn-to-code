package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.EventDefinitionType
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LegacyElementTypeTest {

    @Test
    fun `maps every task kind to its legacy string`() {
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
        expected.forEach { (kind, legacy) ->
            assertThat(LegacyElementType.of(BpmnNodeType.Activity.Task(kind))).isEqualTo(legacy)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*TaskKind.entries.toTypedArray())
    }

    @Test
    fun `maps every gateway kind to its legacy string`() {
        val expected = mapOf(
            GatewayKind.EXCLUSIVE to "EXCLUSIVE_GATEWAY",
            GatewayKind.PARALLEL to "PARALLEL_GATEWAY",
            GatewayKind.INCLUSIVE to "INCLUSIVE_GATEWAY",
            GatewayKind.EVENT_BASED to "EVENT_BASED_GATEWAY",
            GatewayKind.COMPLEX to "COMPLEX_GATEWAY",
        )
        expected.forEach { (kind, legacy) ->
            assertThat(LegacyElementType.of(BpmnNodeType.Gateway(kind))).isEqualTo(legacy)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*GatewayKind.entries.toTypedArray())
    }

    @Test
    fun `maps every subprocess kind to its legacy string`() {
        val expected = mapOf(
            SubProcessKind.PLAIN to "SUB_PROCESS",
            SubProcessKind.EVENT to "EVENT_SUB_PROCESS",
            SubProcessKind.TRANSACTION to "TRANSACTION",
        )
        expected.forEach { (kind, legacy) ->
            assertThat(LegacyElementType.of(BpmnNodeType.Activity.SubProcess(kind))).isEqualTo(legacy)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*SubProcessKind.entries.toTypedArray())
    }

    @Test
    fun `maps call activity to its legacy string`() {
        assertThat(LegacyElementType.of(BpmnNodeType.Activity.CallActivity)).isEqualTo("CALL_ACTIVITY")
    }

    @Test
    fun `maps every event shape to its legacy string`() {
        val expected = mapOf(
            EventShape.START_EVENT to "START_EVENT",
            EventShape.END_EVENT to "END_EVENT",
            EventShape.INTERMEDIATE_CATCH_EVENT to "INTERMEDIATE_CATCH_EVENT",
            EventShape.INTERMEDIATE_THROW_EVENT to "INTERMEDIATE_THROW_EVENT",
            EventShape.BOUNDARY_EVENT to "BOUNDARY_EVENT",
        )
        expected.forEach { (shape, legacy) ->
            assertThat(LegacyElementType.of(BpmnNodeType.Event(shape))).isEqualTo(legacy)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*EventShape.entries.toTypedArray())
    }

    @Test
    fun `event definition type does not affect the legacy string`() {
        EventDefinitionType.entries.forEach { definitionType ->
            assertThat(LegacyElementType.of(BpmnNodeType.Event(EventShape.BOUNDARY_EVENT, definitionType)))
                .isEqualTo("BOUNDARY_EVENT")
        }
    }

    @Test
    fun `maps unknown to its legacy string`() {
        assertThat(LegacyElementType.of(BpmnNodeType.Unknown)).isEqualTo("UNKNOWN")
    }
}
