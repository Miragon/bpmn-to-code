package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.writer.staticListProperty

/**
 * Emits the typed navigation graph of a C# process API `Flow` class: one nested sealed singleton class per flow
 * node, reached as `Flow.<Node>.Instance`. A node carries its metadata (`Id`, `ElementType`, `Name`), its own
 * facets (see [CSharpFacetWriter]), its successors behind `Next`, its outgoing sequence flows behind
 * `OutgoingFlows` (named after the elements they lead to), and — for a subprocess — its interior's start elements
 * behind `Start`. All nodes are direct children of `Flow`, whatever their subprocess depth; a boundary event
 * additionally implements `IBoundaryEvent`. `Flow.All` lists every node.
 *
 * Holder classes (`Successors`, `OutgoingSequenceFlows`, `Interior`) are named differently from the properties
 * that expose them (`Next`, `OutgoingFlows`, `Start`), since C# rejects a member sharing its enclosing type's
 * name (CS0102).
 * Everything that crosses into another node is expression-bodied, so static initialisation never cycles.
 */
internal class CSharpFlowWriter(private val writer: CSharpWriter) {

    private val facetWriter = CSharpFacetWriter(writer)

    fun write(graph: FlowGraph) {
        writer.staticListProperty("All", runtime("IFlowNode"), graph.nodes.map { "${it.objectName}.Instance" }, "Every node of this flow, so tests can check all elements (job workers, deployed ids, …) without reflection.")
        writer.line()
        writer.forEachSeparated(graph.nodes) { node -> writeNode(node) }
    }

    private fun writeNode(node: FlowGraphNode) {
        val nodeInterface = runtime(
            when {
                node.isBoundaryEvent -> "IBoundaryEvent"
                node.eventType != null -> "IEvent"
                else -> "IFlowNode"
            },
        )
        writer.sealedClass(node.objectName, implements = nodeInterface) {
            writer.singleton()
            facetWriter.writeMembers(node.facets)
            writer.line()
            writer.constant("ElementId", node.id)
            writer.readonlyProperty("Id", runtime("ElementId"), "new(ElementId)")
            writer.expressionProperty("ElementType", runtime("BpmnElementType"), CSharpRuntimeTypes.enumMember("BpmnElementType", node.elementType))
            node.eventType?.let { writer.expressionProperty("EventType", runtime("BpmnEventType"), CSharpRuntimeTypes.enumMember("BpmnEventType", it)) }
            writer.expressionProperty("Name", "string?", nullableStringLiteral(node.name))
            facetWriter.writeProperties(node.facets)
            facetWriter.writeHolders(node.facets)
            if (node.successors.isNotEmpty()) {
                writeNodeHolder("Next", "Successors", node.successors)
            }
            if (node.outgoingFlows.isNotEmpty()) {
                writeOutgoingFlows(node.outgoingFlows)
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

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become an `IReadOnlyList<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun writeOutgoingFlows(outgoingFlows: List<FlowsToTarget>) {
        writer.line()
        writer.expressionProperty("OutgoingFlows", "OutgoingSequenceFlows", "new()")
        writer.sealedClass("OutgoingSequenceFlows") {
            outgoingFlows.forEach { flowsToTarget ->
                val flowType = "${runtime("SequenceFlow")}<${flowsToTarget.target.objectName}>"
                val propertyName = flowsToTarget.propertyName.replaceFirstChar { it.uppercaseChar() }
                val constructions = flowsToTarget.flows.map { sequenceFlowConstruction(it, flowsToTarget.target.objectName) }
                when (constructions.size) {
                    1 -> writer.expressionProperty(propertyName, flowType, constructions.single())
                    else -> writer.expressionProperty(propertyName, "System.Collections.Generic.IReadOnlyList<$flowType>", "new $flowType[] { ${constructions.joinToString(", ")} }")
                }
            }
        }
    }

    private fun sequenceFlowConstruction(flow: SequenceFlowEdge, targetObjectName: String): String = listOf(
        "new(${stringLiteral(flow.id)})",
        nullableStringLiteral(flow.name),
        nullableStringLiteral(flow.conditionExpression),
        flow.isDefault.toString(),
        "$targetObjectName.Instance",
    ).joinToString(", ", prefix = "new(", postfix = ")")

    private fun runtime(typeName: String): String = "${CSharpRuntimeTypes.CLASS_NAME}.$typeName"
}
