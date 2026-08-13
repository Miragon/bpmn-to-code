package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind

/**
 * Renders a [FlowNodeDefinition] into its **BPMN element name** (`serviceTask`, `startEvent`, …) for the
 * JSON export. Prefixing the result with `bpmn:` yields the `$type` a `bpmn-moddle` consumer expects.
 *
 * An event's trigger is *not* folded into this name — it lives in `eventDefinitions`, because BPMN allows
 * several triggers on one event. The generated Process API keeps the flattened vocabulary instead, see
 * [ElementTypeName].
 */
internal object BpmnTypeName {

    fun of(node: FlowNodeDefinition): String = when (node) {
        is FlowNodeDefinition.Gateway -> node.kind.render()
        is FlowNodeDefinition.Event -> node.shape.render()
        is FlowNodeDefinition.Activity.Task -> node.kind.render()
        is FlowNodeDefinition.Activity.SubProcess -> node.kind.render()
        is FlowNodeDefinition.Activity.CallActivity -> "callActivity"
        is FlowNodeDefinition.Unknown -> "unknown"
    }

    private fun TaskKind.render(): String = when (this) {
        TaskKind.SERVICE -> "serviceTask"
        TaskKind.USER -> "userTask"
        TaskKind.RECEIVE -> "receiveTask"
        TaskKind.SEND -> "sendTask"
        TaskKind.SCRIPT -> "scriptTask"
        TaskKind.MANUAL -> "manualTask"
        TaskKind.BUSINESS_RULE -> "businessRuleTask"
        TaskKind.NONE -> "task"
    }

    private fun GatewayKind.render(): String = when (this) {
        GatewayKind.EXCLUSIVE -> "exclusiveGateway"
        GatewayKind.PARALLEL -> "parallelGateway"
        GatewayKind.INCLUSIVE -> "inclusiveGateway"
        GatewayKind.EVENT_BASED -> "eventBasedGateway"
        GatewayKind.COMPLEX -> "complexGateway"
    }

    private fun EventShape.render(): String = when (this) {
        EventShape.START_EVENT -> "startEvent"
        EventShape.END_EVENT -> "endEvent"
        EventShape.INTERMEDIATE_CATCH_EVENT -> "intermediateCatchEvent"
        EventShape.INTERMEDIATE_THROW_EVENT -> "intermediateThrowEvent"
        EventShape.BOUNDARY_EVENT -> "boundaryEvent"
    }

    /**
     * Event sub-processes keep the `subProcess` name and are marked by `triggeredByEvent`, as in BPMN.
     */
    private fun SubProcessKind.render(): String = when (this) {
        SubProcessKind.PLAIN, SubProcessKind.EVENT -> "subProcess"
        SubProcessKind.TRANSACTION -> "transaction"
    }
}
