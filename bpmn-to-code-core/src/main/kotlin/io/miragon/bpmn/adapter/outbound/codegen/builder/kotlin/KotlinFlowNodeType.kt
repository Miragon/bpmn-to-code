package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge

/**
 * How generated Kotlin code refers to a flow node as a type. Every node is a plain `object`, so its type is its
 * name ([typeName]). A node that branches additionally has a `Next.<Name>` marker that each of its successors
 * implements ([successorMarker]). The markers share one `Next` holder instead of being nested in their nodes: two
 * branching nodes in a loop would otherwise each need the other's supertypes to resolve the other's marker, which
 * does not compile.
 */
internal class KotlinFlowNodeType(private val objectName: String) {

    constructor(edge: FlowEdge) : this(edge.objectName)

    val typeName: ClassName = ClassName("", objectName)

    val successorMarker: ClassName = ClassName("", NEXT, objectName)

    companion object {
        const val NEXT = "Next"
    }
}
