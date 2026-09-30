package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a C# process API `FlowNodes` class: one nested sealed singleton class per flow
 * node, reached as `FlowNodes.<Node>.Instance`. A node carries its metadata (`Id`, `ElementType`, `Name`), its own
 * facets (see [CSharpFacetWriter]), its successors behind `Next`, its outgoing sequence flows behind
 * `OutgoingFlows` (named after the elements they lead to), and — for a subprocess — its interior's start elements
 * behind `Start`. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a boundary event
 * additionally implements `IBoundaryEvent`. `FlowNodes.All` lists every node.
 *
 * Holder classes (`Successors`, `OutgoingSequenceFlows`, `Interior`) are named differently from the properties
 * that expose them (`Next`, `OutgoingFlows`, `Start`), since C# rejects a member sharing its enclosing type's
 * name (CS0102).
 * Everything that crosses into another node is expression-bodied, so static initialisation never cycles.
 */
internal class CSharpFlowWriter(private val writer: CSharpWriter) {

    private val facetWriter = CSharpFacetWriter(writer)

    fun write(graph: FlowGraph) {
        writer.forEachSeparated(graph.nodes) { node -> writeNode(node) }
        writer.line()
        writer.staticListProperty(name = "All", elementType = CSharpRuntimeTypes.FLOW_NODE, elements = graph.nodes.map { "${it.objectName}.Instance" })
    }

    private fun writeNode(node: FlowGraphNode) {
        val nodeInterface = when {
            node.isBoundaryEvent -> CSharpRuntimeTypes.BOUNDARY_EVENT
            node.eventType != null -> CSharpRuntimeTypes.EVENT
            else -> CSharpRuntimeTypes.FLOW_NODE
        }
        val interfaces = listOf(nodeInterface) + facetWriter.interfaces(node.facets)
        writer.sealedClass(node.objectName, implements = interfaces.joinToString(", ")) {
            writer.singleton()
            writer.line()
            writer.constant("ElementId", node.id)
            writer.readonlyProperty(name = "Id", type = CSharpRuntimeTypes.ELEMENT_ID, initializer = "new(ElementId)")
            writer.expressionProperty(name = "ElementType", type = CSharpRuntimeTypes.BPMN_ELEMENT_TYPE, expression = CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.BPMN_ELEMENT_TYPE, node.elementType))
            node.eventType?.let { writer.expressionProperty(name = "EventType", type = CSharpRuntimeTypes.BPMN_EVENT_TYPE, expression = CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.BPMN_EVENT_TYPE, it)) }
            writer.expressionProperty(name = "Name", type = "string?", expression = nullableStringLiteral(node.name))
            facetWriter.writeProperties(node.facets)
            facetWriter.writeHolders(node.facets)
            if (node.successors.isNotEmpty()) {
                writeNodeHolder(propertyName = "Next", holderName = "Successors", edges = node.successors)
            }
            if (node.outgoingFlows.isNotEmpty()) {
                writeOutgoingFlows(node.outgoingFlows)
            }
            if (node.interiorStarts.isNotEmpty()) {
                writeNodeHolder(propertyName = "Start", holderName = "Interior", edges = node.interiorStarts)
            }
        }
    }

    private fun writeNodeHolder(propertyName: String, holderName: String, edges: List<FlowEdge>) {
        writer.line()
        writer.expressionProperty(name = propertyName, type = holderName, expression = "new()")
        writer.sealedClass(holderName) {
            edges.forEach { edge -> writer.expressionProperty(name = edge.objectName, type = edge.objectName, expression = "${edge.objectName}.Instance") }
        }
    }

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become an `IReadOnlyList<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun writeOutgoingFlows(outgoingFlows: List<FlowsToTarget>) {
        writer.line()
        writer.expressionProperty(name = "OutgoingFlows", type = "OutgoingSequenceFlows", expression = "new()")
        writer.sealedClass("OutgoingSequenceFlows") {
            outgoingFlows.forEach { flowsToTarget ->
                val flowType = "${CSharpRuntimeTypes.SEQUENCE_FLOW}<${flowsToTarget.target.objectName}>"
                val propertyName = flowsToTarget.propertyName.replaceFirstChar { it.uppercaseChar() }
                val constructions = flowsToTarget.flows.map { sequenceFlowConstruction(it, flowsToTarget.target.objectName) }
                when (constructions.size) {
                    1 -> writer.expressionProperty(name = propertyName, type = flowType, expression = constructions.single())
                    else -> writer.expressionProperty(name = propertyName, type = "System.Collections.Generic.IReadOnlyList<$flowType>", expression = "new $flowType[] { ${constructions.joinToString(", ")} }")
                }
            }
        }
    }

    private fun sequenceFlowConstruction(flow: SequenceFlowEdge, targetObjectName: String): String {
        val id = "new(${stringLiteral(flow.id)})"
        val target = "$targetObjectName.Instance"
        val arguments = when {
            flow.hasOnlyDefaults() -> listOf(id, target)
            else -> listOf(id, nullableStringLiteral(flow.name), nullableStringLiteral(flow.conditionExpression), flow.isDefault.toString(), target)
        }
        return arguments.joinToString(", ", prefix = "new(", postfix = ")")
    }
}
