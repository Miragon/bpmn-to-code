package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SuccessorEdge

/**
 * Emits the typed navigation graph of a C# process API `FlowNodes` class: one nested sealed singleton class per flow
 * node, reached as `FlowNodes.<Node>.Instance`. A node carries its metadata (`Id`, `ElementType`, `Name`), its own
 * facets (see [CSharpFacetWriter]), its successors behind `Next` (named after the elements they lead to: the
 * `SequenceFlows` to an element, an attached boundary event, or the compensation handler associated with a
 * compensation boundary event), and — for a subprocess — its interior's start
 * elements behind `Start`. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a boundary event
 * additionally implements `IBoundaryEvent`. `FlowNodes.All` lists every node.
 *
 * Holder classes (`Successors`, `Interior`) are named differently from the properties that expose them (`Next`,
 * `Start`), since C# rejects a member sharing its enclosing type's
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
                writeSuccessors(node)
            }
            if (node.interiorStarts.isNotEmpty()) {
                writeNodeHolder(propertyName = "Start", holderName = "Interior", edges = node.interiorStarts)
            }
        }
    }

    private fun writeSuccessors(node: FlowGraphNode) {
        writer.line()
        writer.expressionProperty(name = "Next", type = "Successors", expression = "new()")
        writer.sealedClass("Successors") {
            node.successors.forEach { successor -> writeSuccessor(successor) }
        }
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent` or an `AssociatedCompensationHandler`. A single flow is created via
     * `SequenceFlows.Single`, several flows to the same target are listed. The target is qualified with `FlowNodes`,
     * as the successor property named after it shadows its type.
     */
    private fun writeSuccessor(successor: SuccessorEdge) {
        val targetName = successor.target.objectName
        val (successorType, expression) = when (successor) {
            is SuccessorEdge.ViaSequenceFlows -> CSharpRuntimeTypes.SEQUENCE_FLOWS to sequenceFlowsConstruction(successor.flows, targetName)
            is SuccessorEdge.AttachedBoundaryEvent -> CSharpRuntimeTypes.ATTACHED_BOUNDARY_EVENT to "new(${targetInstance(targetName)})"
            is SuccessorEdge.AssociatedCompensationHandler -> CSharpRuntimeTypes.ASSOCIATED_COMPENSATION_HANDLER to "new(${targetInstance(targetName)})"
        }
        writer.expressionProperty(name = targetName, type = "$successorType<$targetName>", expression = expression)
    }

    private fun writeNodeHolder(propertyName: String, holderName: String, edges: List<FlowEdge>) {
        writer.line()
        writer.expressionProperty(name = propertyName, type = holderName, expression = "new()")
        writer.sealedClass(holderName) {
            edges.forEach { edge -> writer.expressionProperty(name = edge.objectName, type = edge.objectName, expression = "${edge.objectName}.Instance") }
        }
    }

    private fun sequenceFlowsConstruction(flows: List<SequenceFlowEdge>, targetObjectName: String): String {
        val target = targetInstance(targetObjectName)
        val singleFlow = flows.singleOrNull()
        if (singleFlow != null) {
            return "${CSharpRuntimeTypes.SEQUENCE_FLOWS}.Single(${singleFlowArguments(singleFlow, target)})"
        }
        val flowType = "${CSharpRuntimeTypes.SEQUENCE_FLOW}<$targetObjectName>"
        val constructions = flows.map { flow -> "new(new(${stringLiteral(flow.id)}), ${nullableStringLiteral(flow.name)}, ${nullableStringLiteral(flow.conditionExpression)}, ${flow.isDefault}, $target)" }
        return "new($target, new $flowType[] { ${constructions.joinToString(", ")} })"
    }

    private fun singleFlowArguments(flow: SequenceFlowEdge, target: String): String {
        val metadata = listOfNotNull(
            flow.name?.let { "name: ${stringLiteral(it)}" },
            flow.conditionExpression?.let { "conditionExpression: ${stringLiteral(it)}" },
            "isDefault: true".takeIf { flow.isDefault },
        )
        return (listOf("new(${stringLiteral(flow.id)})", target) + metadata).joinToString(", ")
    }

    private fun targetInstance(objectName: String): String = "FlowNodes.$objectName.Instance"
}
