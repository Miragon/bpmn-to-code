package io.miragon.bpmn.adapter.outbound.codegen.flow

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * Derives the navigation identifiers for elements and sequence flows.
 *
 * Names come from the id's [toCamelCase] form — the one guarded by the mandatory `collision-detection`
 * rule. That rule rejects any model whose ids collapse to the same name before generation runs, so across the
 * whole model the names are already guaranteed unique and no disambiguation is needed here.
 *
 * - object name = PascalCase of the id (`serviceTask_increment` -> `ServiceTaskIncrement`)
 * - property/accessor name = the same, first letter lowercased (`serviceTaskIncrement`)
 * - outgoing-flows property = `to` + the target's object name (`toEndEventNoSubscribers`); the target is a
 *   node, so the name inherits its uniqueness
 */
internal object FlowNaming {

    /**
     * Names every node of the model by the edge that reaches it, keyed by element id.
     */
    fun assign(nodes: List<FlowNodeWithId>): Map<String, FlowEdge> = nodes.associate { node ->
        val objectName = node.definition.id.orEmpty().toCamelCase()
        node.id to FlowEdge(propertyName = decapitalize(objectName), objectName = objectName)
    }

    private fun decapitalize(name: String): String = name.replaceFirstChar { it.lowercaseChar() }
}
