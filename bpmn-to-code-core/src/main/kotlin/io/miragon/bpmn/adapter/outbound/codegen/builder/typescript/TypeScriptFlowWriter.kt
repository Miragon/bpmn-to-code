package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge

/**
 * Emits one exported singleton class per element, reached as `FlowNodes.<Node>`. A node carries its metadata, its
 * facets (see [TypeScriptFacetWriter]), its successors behind `next` and, for a subprocess, the start elements of its
 * interior behind `startEvents`. Everything that leads to another node is a getter, so no class touches another one
 * while the module loads.
 */
internal class TypeScriptFlowWriter(private val writer: TypeScriptWriter) {

    private val facetWriter = TypeScriptFacetWriter(writer)

    fun writeNodeClasses(graph: FlowGraph) {
        graph.nodes.forEach { node ->
            writeNodeClass(node)
            writer.line()
        }
    }

    fun writeFlowNodes(graph: FlowGraph) {
        writer.objectProperty("FlowNodes") {
            graph.nodes.forEach { node -> writer.property(node.objectName, instanceOf(node.objectName)) }
            val type = "readonly ${writer.imports.runtimeType("FlowNode")}[]"
            writer.listGetter(key = "all", type = type, elements = graph.nodes.map { instanceOf(it.objectName) })
        }
    }

    private fun writeNodeClass(node: FlowGraphNode) {
        writer.exportedClass(node.objectName, implements = interfacesOf(node)) {
            writer.line("static readonly INSTANCE = new ${node.objectName}();")
            writer.line()
            writer.line("private constructor() {}")
            writer.line()
            writer.field("ELEMENT_ID", stringLiteral(node.id))
            writer.field("id", "this.ELEMENT_ID")
            writer.field("elementType", stringLiteral(node.elementType))
            node.eventType?.let { writer.field("eventType", stringLiteral(it)) }
            writer.field("name", nullableStringLiteral(node.name))
            facetWriter.writeFields(node.facets)
            facetWriter.writeHolders(node.facets)
            writeSuccessors(node.successors)
            writeInteriorStarts(node)
        }
    }

    private fun interfacesOf(node: FlowGraphNode): List<String> {
        val host = node.facets.attachedTo?.objectName
        val nodeInterface = when {
            node.isBoundaryEvent && host != null -> writer.imports.runtimeType("BoundaryEvent<$host>")
            node.eventType != null -> writer.imports.runtimeType("Event")
            else -> writer.imports.runtimeType("FlowNode")
        }
        val navigation = listOfNotNull(
            writer.imports.runtimeType("HasSuccessors").takeIf { node.successors.isNotEmpty() },
            writer.imports.runtimeType("FlowScope").takeIf { node.interiorStarts.isNotEmpty() },
        )
        val facets = node.facets.facetInterfaces.map { writer.imports.runtimeType(it.typeName) }
        return listOf(nodeInterface) + navigation + facets
    }

    private fun writeSuccessors(successors: List<SuccessorEdge>) {
        if (successors.isEmpty()) {
            return
        }
        writer.line()
        writer.objectField("next") {
            successors.forEach { successor -> writeSuccessor(successor) }
        }
    }

    private fun writeSuccessor(successor: SuccessorEdge) {
        val target = successor.target.objectName
        val (type, expression) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> {
                val flows = successor.flows.joinToString(", ") { flowLiteral(it) }
                "SequenceFlows" to "${writer.imports.runtimeValue("sequenceFlows")}(${instanceOf(target)}, $flows)"
            }

            is SuccessorEdge.AttachedBoundaryEvent -> {
                "AttachedBoundaryEvent" to "${writer.imports.runtimeValue("attachedBoundaryEvent")}(${instanceOf(target)})"
            }

            is SuccessorEdge.AssociatedCompensationHandler -> {
                "AssociatedCompensationHandler" to "${writer.imports.runtimeValue("associatedCompensationHandler")}(${instanceOf(target)})"
            }
        }
        writer.objectGetter(key = successor.target.propertyName, type = writer.imports.runtimeType("$type<$target>"), expression = expression)
    }

    private fun writeInteriorStarts(node: FlowGraphNode) {
        if (node.interiorStarts.isEmpty()) {
            return
        }
        writer.line()
        writer.objectField("startEvents") {
            node.interiorStarts.forEach { start ->
                writer.objectGetter(key = start.propertyName, type = start.objectName, expression = instanceOf(start.objectName))
            }
        }
    }

    private fun flowLiteral(flow: SequenceFlowEdge): String {
        val fields = listOfNotNull(
            "id: ${stringLiteral(flow.id)}",
            flow.name?.let { "name: ${stringLiteral(it)}" },
            flow.conditionExpression?.let { "conditionExpression: ${stringLiteral(it)}" },
            "isDefault: true".takeIf { flow.isDefault },
        )
        return fields.joinToString(", ", prefix = "{ ", postfix = " }")
    }

    companion object {

        fun instanceOf(objectName: String): String = "$objectName.INSTANCE"
    }
}
