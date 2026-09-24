package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.stringLiteral

/**
 * Emits the typed navigation graph of a C# process API `Flow` class: one nested sealed singleton class per flow
 * node, reached as `Flow.<Node>.Instance`. A node carries its metadata (`Id`, `ElementType`, `Name`), its own
 * facets (see [CSharpFacetWriter]), its successors behind `Next`, its outgoing sequence flows as typed edges
 * behind `Flows`, and — for a subprocess — its interior's start elements behind `Start`. All nodes are direct
 * children of `Flow`, whatever their subprocess depth.
 *
 * Holder classes (`Successors`, `SequenceFlows`, `Interior`) are named differently from the properties that
 * expose them (`Next`, `Flows`, `Start`), since C# rejects a member sharing its enclosing type's name (CS0102).
 * Everything that crosses into another node is expression-bodied, so static initialisation never cycles.
 */
internal class CSharpFlowWriter(private val writer: CSharpWriter) {

    private val facetWriter = CSharpFacetWriter(writer)

    fun write(graph: FlowGraph) {
        writer.forEachSeparated(graph.nodes) { node -> writeNode(node) }
    }

    private fun writeNode(node: FlowGraphNode) {
        writer.sealedClass(node.objectName, implements = runtime("IFlowNode")) {
            writer.singleton()
            facetWriter.writeMembers(node.facets)
            writer.line()
            writer.readonlyProperty("Id", runtime("ElementId"), "new(${stringLiteral(node.id)})")
            writer.expressionProperty("ElementType", "string", stringLiteral(node.elementType))
            writer.expressionProperty("Name", "string?", nullableStringLiteral(node.name))
            facetWriter.writeProperties(node.facets)
            facetWriter.writeHolders(node.facets)
            if (node.successors.isNotEmpty()) {
                writeNodeHolder("Next", "Successors", node.successors)
            }
            if (node.flows.isNotEmpty()) {
                writeFlows(node.flows)
            }
            if (node.interiorStarts.isNotEmpty()) {
                writeNodeHolder("Start", "Interior", node.interiorStarts)
            }
        }
    }

    private fun writeNodeHolder(propertyName: String, holderName: String, edges: List<FlowEdge>) {
        writer.line()
        writer.expressionProperty(propertyName, holderName, "new()")
        writer.sealedClass(holderName) {
            edges.forEach { edge -> writer.expressionProperty(edge.objectName, edge.objectName, "${edge.objectName}.Instance") }
        }
    }

    private fun writeFlows(flows: List<SequenceFlowEdge>) {
        writer.line()
        writer.expressionProperty("Flows", "SequenceFlows", "new()")
        writer.sealedClass("SequenceFlows") {
            flows.forEach { edge ->
                val edgeType = "${runtime("SequenceFlow")}<${edge.target.objectName}>"
                writer.expressionProperty(edge.propertyName.replaceFirstChar { it.uppercaseChar() }, edgeType, edgeExpression(edge))
            }
        }
    }

    private fun edgeExpression(edge: SequenceFlowEdge): String = listOf(
        "new(${stringLiteral(edge.id)})",
        nullableStringLiteral(edge.name),
        nullableStringLiteral(edge.conditionExpression),
        edge.isDefault.toString(),
        "${edge.target.objectName}.Instance",
    ).joinToString(", ", prefix = "new(", postfix = ")")

    private fun runtime(typeName: String): String = "${CSharpRuntimeTypes.CLASS_NAME}.$typeName"
}
