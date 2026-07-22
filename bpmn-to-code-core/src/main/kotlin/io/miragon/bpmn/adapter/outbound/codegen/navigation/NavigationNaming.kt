package io.miragon.bpmn.adapter.outbound.codegen.navigation

import io.miragon.bpmn.domain.shared.FlowNodeDefinition

/**
 * Derives the navigation identifiers for elements.
 *
 * Names are derived from the element's sanitized constant name (`FlowNodeDefinition.getName()`, the same
 * UPPER_SNAKE basis used for the flat `Elements` constants and checked by `CollisionDetectionService`), so
 * navigation names carry no new collision surface beyond the existing constants:
 *
 * - object name = PascalCase of the constant name (`SERVICE_TASK_INCREMENT` -> `ServiceTaskIncrement`)
 * - property/accessor name = the same, first letter lowercased (`serviceTaskIncrement`)
 *
 * Within a single scope, names must be unique. On the rare event that two distinct ids normalize to the same
 * name, they are disambiguated deterministically (ordered by id, numeric suffix), so regeneration is idempotent.
 */
object NavigationNaming {

    /** Stable object/property names for one node, unique within its scope. */
    data class Names(val objectName: String, val propertyName: String)

    /**
     * Assigns unique [Names] to every node in a scope, keyed by element id.
     * Ordered by id and suffixed deterministically on collision so the result is stable across regenerations.
     */
    fun assignScope(nodes: List<FlowNodeDefinition>): Map<String, Names> {
        val taken = mutableSetOf<String>()
        val result = mutableMapOf<String, Names>()
        nodes
            .filter { it.id != null }
            .sortedBy { it.id }
            .forEach { node ->
                val objectName = uniqueName(pascalCase(node.getName()), taken)
                taken.add(objectName)
                result[node.id!!] = Names(objectName, decapitalize(objectName))
            }
        return result
    }

    private fun uniqueName(base: String, taken: Set<String>): String {
        val candidate = base.ifEmpty { "Element" }
        if (candidate !in taken) {
            return candidate
        }
        var index = 2
        while ("$candidate$index" in taken) {
            index++
        }
        return "$candidate$index"
    }

    private fun pascalCase(upperSnake: String): String {
        return upperSnake
            .split("_")
            .filter { it.isNotEmpty() }
            .joinToString("") { segment -> segment.lowercase().replaceFirstChar { it.uppercaseChar() } }
    }

    private fun decapitalize(name: String): String {
        return name.replaceFirstChar { it.lowercaseChar() }
    }
}
