package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnTypeNameTest {

    @Test
    fun `maps every task kind to its bpmn element name`() {
        val expected = mapOf(
            TaskKind.SERVICE to "serviceTask",
            TaskKind.USER to "userTask",
            TaskKind.RECEIVE to "receiveTask",
            TaskKind.SEND to "sendTask",
            TaskKind.SCRIPT to "scriptTask",
            TaskKind.MANUAL to "manualTask",
            TaskKind.BUSINESS_RULE to "businessRuleTask",
            TaskKind.NONE to "task",
        )
        expected.forEach { (kind, name) ->
            assertThat(BpmnTypeName.of(FlowNodeDefinition.Activity.Task(id = "task", kind = kind))).isEqualTo(name)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*TaskKind.entries.toTypedArray())
    }

    @Test
    fun `maps every gateway kind to its bpmn element name`() {
        val expected = mapOf(
            GatewayKind.EXCLUSIVE to "exclusiveGateway",
            GatewayKind.PARALLEL to "parallelGateway",
            GatewayKind.INCLUSIVE to "inclusiveGateway",
            GatewayKind.EVENT_BASED to "eventBasedGateway",
            GatewayKind.COMPLEX to "complexGateway",
        )
        expected.forEach { (kind, name) ->
            assertThat(BpmnTypeName.of(FlowNodeDefinition.Gateway(id = "gw", kind = kind))).isEqualTo(name)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*GatewayKind.entries.toTypedArray())
    }

    @Test
    fun `maps every event shape to its bpmn element name`() {
        val expected = mapOf(
            EventShape.START_EVENT to "startEvent",
            EventShape.END_EVENT to "endEvent",
            EventShape.INTERMEDIATE_CATCH_EVENT to "intermediateCatchEvent",
            EventShape.INTERMEDIATE_THROW_EVENT to "intermediateThrowEvent",
            EventShape.BOUNDARY_EVENT to "boundaryEvent",
        )
        expected.forEach { (shape, name) ->
            assertThat(BpmnTypeName.of(FlowNodeDefinition.Event(id = "event", shape = shape))).isEqualTo(name)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*EventShape.entries.toTypedArray())
    }

    @Test
    fun `maps every sub-process kind, keeping event sub-processes as subProcess`() {
        val expected = mapOf(
            SubProcessKind.PLAIN to "subProcess",
            SubProcessKind.EVENT to "subProcess",
            SubProcessKind.TRANSACTION to "transaction",
        )
        expected.forEach { (kind, name) ->
            assertThat(BpmnTypeName.of(FlowNodeDefinition.Activity.SubProcess(id = "sub", kind = kind))).isEqualTo(name)
        }
        assertThat(expected.keys).containsExactlyInAnyOrder(*SubProcessKind.entries.toTypedArray())
    }

    @Test
    fun `maps call activity and unknown nodes`() {
        assertThat(
            BpmnTypeName.of(
                FlowNodeDefinition.Activity.CallActivity(id = "call", definition = CallActivityDefinition("call", "target")),
            ),
        ).isEqualTo("callActivity")
        assertThat(BpmnTypeName.of(FlowNodeDefinition.Unknown(id = "x"))).isEqualTo("unknown")
    }
}
