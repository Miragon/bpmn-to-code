package io.miragon.bpmn.adapter.outbound.codegen.flow

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
 * - sequence-flow property = the flow id's property form (`flow_noSubscribers` -> `flowNoSubscribers`)
 */
internal object FlowNaming {

    /**
     * Object/property names for one node.
     */
    data class Names(val objectName: String, val propertyName: String)

    /**
     * Assigns [Names] to every node of the model, keyed by element id.
     */
    fun assign(nodes: List<FlowNodeWithId>): Map<String, Names> = nodes.associate { node ->
        val objectName = node.definition.getRawName().toCamelCase()
        node.id to Names(objectName, decapitalize(objectName))
    }

    fun flowProperty(flowId: String): String = decapitalize(flowId.toCamelCase())

    private fun decapitalize(name: String): String = name.replaceFirstChar { it.lowercaseChar() }
}
