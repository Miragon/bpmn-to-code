package io.miragon.bpmn.adapter.outbound.codegen.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.ProcessApiGraph

/**
 * The names of the types a Process API declares itself. A referenced type of the same simple name would be shadowed by
 * them, so it is written fully qualified.
 */
internal object GeneratedTypeNames {

    private val HOLDER_NAMES = setOf("FlowNodes", "Next", "Start", "Variables", "Inputs", "Outputs")

    fun of(api: ProcessApiGraph): Set<String> {
        val nodeNames = api.flow?.nodes.orEmpty().map { it.objectName }
        return setOf(api.typeName.value) + HOLDER_NAMES + nodeNames
    }
}
