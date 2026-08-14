package io.miragon.bpmn.adapter.outbound.shared

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind

/**
 * Renders a [FlowNodeDefinition] into the flat `elementType` string used by the **generated Process API**
 * navigation nodes (each `Relations` node's `elementType`, carried through `NavigationNode.elementType`).
 *
 * Tasks, gateways and activities map to their flat name; an event surfaces its first event definition as a
 * prefix on the shape (e.g. `ERROR_BOUNDARY_EVENT`), so consumers can tell a timer from an error without
 * cross-referencing. The JSON export uses the BPMN element names instead — see `BpmnTypeName`.
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

    private fun FlowNodeDefinition.Event.render(): String {
        val definitionType = eventDefinitions.map { it.type }.firstOrNull { it in PREFIXED_TYPES }
            ?: return shape.name
        return "${definitionType.name}_${shape.name}"
    }

    /**
     * The event-definition kinds that surface as a prefix on the flat element type. Conditional, link and
     * terminate carry no prefix — the flat Process API vocabulary renders them shape-only, e.g. `END_EVENT`.
     */
    private val PREFIXED_TYPES = setOf(
        EventDefinitionInstance.Type.TIMER,
        EventDefinitionInstance.Type.MESSAGE,
        EventDefinitionInstance.Type.ERROR,
        EventDefinitionInstance.Type.SIGNAL,
        EventDefinitionInstance.Type.ESCALATION,
        EventDefinitionInstance.Type.COMPENSATION,
    )

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
