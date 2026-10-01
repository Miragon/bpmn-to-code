package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a C# process API `FlowNodes` class: one nested sealed singleton class per flow
 * node, reached as `FlowNodes.<Node>.Instance`. A node carries its metadata (`Id`, `ElementType`, `Name`), its own
 * facets (see [CSharpFacetWriter]), its successors in `Outgoing` (the `SequenceFlows` to an element, or an attached
 * boundary event), and — for a subprocess — its interior's start elements behind `Start`. The edge lives in the type
 * hierarchy: a node leading to one element is an `ILeadsTo` that element, and a node leading to several is an
 * `ILeadsTo` its `Next.<Node>` marker, which every one of its successors implements; `FlowsTo` accepts only such an
 * element. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a boundary event
 * additionally implements `IBoundaryEvent`. `FlowNodes.All` lists every node.
 *
 * The holder class `Interior` is named differently from the property that exposes it (`Start`), since C# rejects a
 * member sharing its enclosing type's name (CS0102).
 * Everything that crosses into another node is expression-bodied, so static initialisation never cycles.
 */
internal class CSharpFlowWriter(private val writer: CSharpWriter) {

    private val facetWriter = CSharpFacetWriter(writer)

    fun write(graph: FlowGraph) {
        writer.forEachSeparated(graph.nodes) { node -> writeNode(node) }
        writer.line()
        val branchingNodes = graph.nodes.filter { it.branches }
        if (branchingNodes.isNotEmpty()) {
            writer.docComment("One marker per element leading to several others, implemented by every element it leads to.")
            writer.staticClass(SUCCESSOR_MARKERS) {
                branchingNodes.forEach { node -> writer.line("public interface ${node.objectName} : ${CSharpRuntimeTypes.FLOW_NODE} { }") }
            }
            writer.line()
        }
        writer.listProperty(name = "All", elementType = CSharpRuntimeTypes.FLOW_NODE, elements = graph.nodes.map { "${it.objectName}.Instance" }, modifiers = "public static")
    }

    private fun writeNode(node: FlowGraphNode) {
        val nodeInterface = when {
            node.isBoundaryEvent -> CSharpRuntimeTypes.BOUNDARY_EVENT
            node.eventType != null -> CSharpRuntimeTypes.EVENT
            else -> CSharpRuntimeTypes.FLOW_NODE
        }
        val leadsTo = if (node.successors.isEmpty()) emptyList() else listOf("${CSharpRuntimeTypes.LEADS_TO}<${followingType(node)}>")
        val predecessorMarkers = node.predecessors.filter { it.branches }.map { successorMarker(it.objectName) }
        val interfaces = listOf(nodeInterface) + leadsTo + predecessorMarkers + facetWriter.interfaces(node.facets)
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

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent`. A single flow is created via `SequenceFlows.Single`, several flows to the same target
     * are listed. `FlowsTo` is generic for a branching node, as only an interface can constrain a type parameter.
     */
    private fun writeSuccessors(node: FlowGraphNode) {
        val following = followingType(node)
        val successors = node.successors.map { successor ->
            val flowsToTarget = node.outgoingFlows.find { it.target.objectName == successor.objectName }
            flowsToTarget?.let { sequenceFlowsConstruction(it.flows, successor.objectName) }
                ?: "new ${CSharpRuntimeTypes.ATTACHED_BOUNDARY_EVENT}<${successor.objectName}>(${targetInstance(successor.objectName)})"
        }
        writer.line()
        writer.listProperty(name = "Outgoing", elementType = "${CSharpRuntimeTypes.SUCCESSOR}<$following>", elements = successors)
        if (node.outgoingFlows.isEmpty()) return
        val flowsTo = "${CSharpRuntimeTypes.SEQUENCE_FLOWS}.To(Outgoing, target)"
        when {
            node.branches -> writer.line("public ${CSharpRuntimeTypes.SEQUENCE_FLOWS}<TTarget> FlowsTo<TTarget>(TTarget target) where TTarget : $following => $flowsTo;")
            else -> writer.line("public ${CSharpRuntimeTypes.SEQUENCE_FLOWS}<$following> FlowsTo($following target) => $flowsTo;")
        }
    }

    private fun followingType(node: FlowGraphNode): String = when {
        node.branches -> successorMarker(node.objectName)
        else -> node.successors.single().objectName
    }

    private fun successorMarker(objectName: String): String = "$SUCCESSOR_MARKERS.$objectName"

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
        return "new ${CSharpRuntimeTypes.SEQUENCE_FLOWS}<$targetObjectName>($target, new $flowType[] { ${constructions.joinToString(", ")} })"
    }

    private fun singleFlowArguments(flow: SequenceFlowEdge, target: String): String {
        val metadata = listOfNotNull(
            flow.name?.let { "name: ${stringLiteral(it)}" },
            flow.conditionExpression?.let { "conditionExpression: ${stringLiteral(it)}" },
            "isDefault: true".takeIf { flow.isDefault },
        )
        return (listOf("new(${stringLiteral(flow.id)})", target) + metadata).joinToString(", ")
    }

    private fun targetInstance(objectName: String): String = "$objectName.Instance"

    private companion object {
        private const val SUCCESSOR_MARKERS = "Next"
    }
}
