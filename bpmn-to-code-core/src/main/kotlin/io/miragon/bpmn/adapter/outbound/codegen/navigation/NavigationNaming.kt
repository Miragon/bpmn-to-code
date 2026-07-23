package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase

/**
 * Derives the navigation identifiers for elements.
 *
 * Names come from the element id's [toCamelCase] form — the same basis the `CallActivities` / `Variables`
 * nested objects use, and the one guarded by the mandatory `collision-detection` rule. That rule rejects any
 * model whose ids collapse to the same name before generation runs, so within a scope the names are already
 * guaranteed unique and no disambiguation is needed here.
 *
 * - object name = PascalCase of the id (`serviceTask_increment` -> `ServiceTaskIncrement`)
 * - property/accessor name = the same, first letter lowercased (`serviceTaskIncrement`)
 */
object NavigationNaming {

    /**
     * Object/property names for one node.
     */
    data class Names(val objectName: String, val propertyName: String)

    /**
     * Assigns [Names] to every node in a scope, keyed by element id.
     */
    fun assignScope(nodes: List<FlowNodeDefinition>): Map<String, Names> {
        return nodes
            .filter { it.id != null }
            .associate { node ->
                val objectName = node.getRawName().toCamelCase()
                node.id!! to Names(objectName, decapitalize(objectName))
            }
    }

    private fun decapitalize(name: String): String {
        return name.replaceFirstChar { it.lowercaseChar() }
    }
}
