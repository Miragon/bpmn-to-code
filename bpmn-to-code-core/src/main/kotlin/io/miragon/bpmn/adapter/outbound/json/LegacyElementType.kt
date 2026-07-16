package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.domain.shared.BpmnNodeType
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind

/**
 * Translates the two-axis [BpmnNodeType] domain model back into the flat legacy element-type
 * string that the generated JSON has always used.
 *
 * This is the single, lossless boundary between the internal node-type model and the frozen
 * output vocabulary. The event [definitionType][BpmnNodeType.Event.definitionType] is intentionally
 * dropped on output: today's JSON only knows the event shape.
 */
object LegacyElementType {

    fun of(nodeType: BpmnNodeType): String = when (nodeType) {
        is BpmnNodeType.Gateway -> nodeType.kind.legacyName()
        is BpmnNodeType.Event -> nodeType.shape.name
        is BpmnNodeType.Activity.Task -> nodeType.kind.legacyName()
        is BpmnNodeType.Activity.SubProcess -> nodeType.kind.legacyName()
        is BpmnNodeType.Activity.CallActivity -> "CALL_ACTIVITY"
        is BpmnNodeType.Unknown -> "UNKNOWN"
    }

    private fun TaskKind.legacyName(): String = when (this) {
        TaskKind.SERVICE -> "SERVICE_TASK"
        TaskKind.USER -> "USER_TASK"
        TaskKind.RECEIVE -> "RECEIVE_TASK"
        TaskKind.SEND -> "SEND_TASK"
        TaskKind.SCRIPT -> "SCRIPT_TASK"
        TaskKind.MANUAL -> "MANUAL_TASK"
        TaskKind.BUSINESS_RULE -> "BUSINESS_RULE_TASK"
        TaskKind.NONE -> "TASK"
    }

    private fun GatewayKind.legacyName(): String = when (this) {
        GatewayKind.EXCLUSIVE -> "EXCLUSIVE_GATEWAY"
        GatewayKind.PARALLEL -> "PARALLEL_GATEWAY"
        GatewayKind.INCLUSIVE -> "INCLUSIVE_GATEWAY"
        GatewayKind.EVENT_BASED -> "EVENT_BASED_GATEWAY"
        GatewayKind.COMPLEX -> "COMPLEX_GATEWAY"
    }

    private fun SubProcessKind.legacyName(): String = when (this) {
        SubProcessKind.PLAIN -> "SUB_PROCESS"
        SubProcessKind.EVENT -> "EVENT_SUB_PROCESS"
        SubProcessKind.TRANSACTION -> "TRANSACTION"
    }
}
