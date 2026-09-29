package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind

/**
 * Renders a [FlowNodeDefinition] into the `BpmnElementType` / `BpmnEventType` constant names used by the
 * **generated Process API** navigation nodes. An event's shape and its definition are separate axes: a timer
 * boundary event is `BOUNDARY_EVENT` with event type `TIMER`. The JSON export uses the BPMN element names
 * instead — see `BpmnTypeName`.
 */
internal object ElementTypeName {

    fun of(node: FlowNodeDefinition): String = when (node) {
        is FlowNodeDefinition.Gateway -> node.kind.render()
        is FlowNodeDefinition.Event -> node.render()
        is FlowNodeDefinition.Activity.Task -> node.kind.render()
        is FlowNodeDefinition.Activity.SubProcess -> node.kind.render()
        is FlowNodeDefinition.Activity.CallActivity -> "CALL_ACTIVITY"
        is FlowNodeDefinition.Unknown -> "UNKNOWN"
    }

    private fun FlowNodeDefinition.Event.render(): String = shape.name

    fun eventTypeOf(event: FlowNodeDefinition.Event): String = when (event.eventDefinitions.size) {
        0 -> "NONE"
        1 -> event.eventDefinitions.single().type.name
        else -> "MULTIPLE"
    }

    private fun TaskKind.render(): String = when (this) {
        TaskKind.SERVICE -> "SERVICE_TASK"
        TaskKind.USER -> "USER_TASK"
        TaskKind.RECEIVE -> "RECEIVE_TASK"
        TaskKind.SEND -> "SEND_TASK"
        TaskKind.SCRIPT -> "SCRIPT_TASK"
        TaskKind.MANUAL -> "MANUAL_TASK"
        TaskKind.BUSINESS_RULE -> "BUSINESS_RULE_TASK"
        TaskKind.NONE -> "TASK"
    }

    private fun GatewayKind.render(): String = when (this) {
        GatewayKind.EXCLUSIVE -> "EXCLUSIVE_GATEWAY"
        GatewayKind.PARALLEL -> "PARALLEL_GATEWAY"
        GatewayKind.INCLUSIVE -> "INCLUSIVE_GATEWAY"
        GatewayKind.EVENT_BASED -> "EVENT_BASED_GATEWAY"
        GatewayKind.COMPLEX -> "COMPLEX_GATEWAY"
    }

    private fun SubProcessKind.render(): String = when (this) {
        SubProcessKind.PLAIN -> "SUB_PROCESS"
        SubProcessKind.EVENT -> "EVENT_SUB_PROCESS"
        SubProcessKind.TRANSACTION -> "TRANSACTION"
    }
}
