package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.EventDefinitionType
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind

/**
 * Renders the two-axis [BpmnNodeType] domain model into the flat `elementType` string shared by the
 * outbound representations — the generated JSON export and the generated Process API.
 *
 * This is the single boundary between the internal node-type model and that output vocabulary.
 * Tasks, gateways and activities map to their flat name; event nodes surface their concrete
 * [definitionType][BpmnNodeType.Event.definitionType] as a prefix on the shape
 * (e.g. `ERROR_BOUNDARY_EVENT`), so consumers can tell a timer from an error without cross-referencing.
 */
object ElementTypeName {

    fun of(nodeType: BpmnNodeType): String = when (nodeType) {
        is BpmnNodeType.Gateway -> nodeType.kind.render()
        is BpmnNodeType.Event -> nodeType.render()
        is BpmnNodeType.Activity.Task -> nodeType.kind.render()
        is BpmnNodeType.Activity.SubProcess -> nodeType.kind.render()
        is BpmnNodeType.Activity.CallActivity -> "CALL_ACTIVITY"
        is BpmnNodeType.Unknown -> "UNKNOWN"
    }

    private fun BpmnNodeType.Event.render(): String {
        return if (definitionType == EventDefinitionType.NONE) {
            shape.name
        } else {
            "${definitionType.name}_${shape.name}"
        }
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
