package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode

/**
 * The runtime interfaces a Kotlin or Java flow node implements, e.g.
 * `HasSuccessors<ServiceTaskSendContract.Next>, HasJobType, HasVariables`.
 *
 * A bare `Next` or `Start` would bind to an enclosing type's holder of that name, so both are qualified with the node.
 */
internal class JvmSupertypes(private val types: TypeReferences, private val syntax: JvmSyntax) {

    fun of(node: FlowGraphNode): List<String> {
        val objectName = syntax.identifier(node.objectName)
        return buildList {
            if (node.successors.isNotEmpty()) {
                add("${types.of("HasSuccessors")}<$objectName.Next>")
            }
            addAll(listOfNotNull(eventInterfaceOf(node)))
            if (node.interiorStarts.isNotEmpty()) {
                add("${types.of("FlowScope")}<$objectName.Start>")
            }
            addAll(node.facets.facetInterfaces.map { types.of(it.typeName) })
        }
    }

    /**
     * e.g. `BoundaryEvent<ServiceTaskValidateApplication>` for a boundary event, `Event` for any other event
     */
    private fun eventInterfaceOf(node: FlowGraphNode): String? {
        val host = node.facets.attachedTo
        if (node.isBoundaryEvent && host != null) {
            return "${types.of("BoundaryEvent")}<${syntax.identifier(host.objectName)}>"
        }
        if (node.eventType != null) {
            return types.of("Event")
        }
        return null
    }
}
