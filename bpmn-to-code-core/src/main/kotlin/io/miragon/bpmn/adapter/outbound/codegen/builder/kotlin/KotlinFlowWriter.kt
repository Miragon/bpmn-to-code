package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a Kotlin process API `FlowNodes` object: one nested node object per flow
 * node, carrying its metadata via `AbstractFlowNode`, its own facets (see [KotlinFacetWriter]) and its successors
 * in `outgoing`: the `SequenceFlows` to an element, or an attached boundary event. The edge lives in the type
 * hierarchy: a node leading to one element is a `LeadsTo` that element, and a node leading to several nests a
 * `Next` marker that every one of its successors implements and is a `LeadsTo` of that (see [KotlinFlowNodeType]). All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a subprocess node
 * additionally is a `FlowScope` whose `startEvents` yields the interior's start elements, and a boundary event is
 * a `BoundaryEvent` of its host. `FlowNodes.entries` lists every node.
 */
internal class KotlinFlowWriter {

    private val facetWriter = KotlinFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        builder.addProperty(entries(graph))
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
        val branchingNodes = graph.nodes.filter { it.branches }
        if (branchingNodes.isNotEmpty()) {
            builder.addType(successorMarkers(branchingNodes))
        }
    }

    private fun entries(graph: FlowGraph): PropertySpec = PropertySpec.builder("entries", LIST.parameterizedBy(KotlinRuntimeTypes.FLOW_NODE))
        .initializer(KotlinCodeFormat.listOfNames(graph.nodes.map { it.objectName })).build()

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
        nodeBuilder.addProperty(PropertySpec.builder(ELEMENT_ID, String::class).addModifiers(KModifier.CONST).initializer("%L", stringLiteral(node.id)).build())
        facetWriter.properties(node.facets).forEach { nodeBuilder.addProperty(it) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(nodeBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            nodeBuilder.addProperty(accessorProperty("startEvents", START_HOLDER))
        }
        facetWriter.holders(node.facets).forEach { nodeBuilder.addType(it) }
        interiorStartsHolder(node)?.let { nodeBuilder.addType(it) }
        return nodeBuilder.build()
    }

    /** The `Next` holder: one marker per branching node, implemented by each of that node's successors. */
    private fun successorMarkers(branchingNodes: List<FlowGraphNode>): TypeSpec {
        val holder = TypeSpec.objectBuilder(KotlinFlowNodeType.NEXT)
            .addKdoc("One marker per element leading to several others, implemented by every element it leads to.")
        branchingNodes.forEach { node ->
            holder.addType(TypeSpec.interfaceBuilder(node.objectName).addSuperinterface(KotlinRuntimeTypes.FLOW_NODE).build())
        }
        return holder.build()
    }

    private fun extendFlowNode(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.superclass(KotlinRuntimeTypes.ABSTRACT_FLOW_NODE)
            .addSuperclassConstructorParameter(superclassArguments(node))
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(KotlinRuntimeTypes.LEADS_TO.parameterizedBy(followingType(node)))
        }
        node.predecessors.filter { it.branches }.forEach { predecessor -> nodeBuilder.addSuperinterface(KotlinFlowNodeType(predecessor).successorMarker) }
        val host = node.facets.attachedTo
        when {
            node.isBoundaryEvent && host != null ->
                nodeBuilder.addSuperinterface(KotlinRuntimeTypes.BOUNDARY_EVENT.parameterizedBy(KotlinFlowNodeType(host).typeName))

            node.eventType != null -> nodeBuilder.addSuperinterface(KotlinRuntimeTypes.EVENT)
        }
        if (node.interiorStarts.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface(interfaceType = KotlinRuntimeTypes.FLOW_SCOPE, node = node, holderName = START_HOLDER))
        }
        nodeBuilder.addSuperinterfaces(facetWriter.superinterfaces(node.facets))
        node.eventType?.let { eventType ->
            val eventTypeClass = KotlinRuntimeTypes.BPMN_EVENT_TYPE
            nodeBuilder.addProperty(PropertySpec.builder("eventType", eventTypeClass, KModifier.OVERRIDE).initializer("%T.%L", eventTypeClass, eventType).build())
        }
    }

    private fun superclassArguments(node: FlowGraphNode): CodeBlock = KotlinCodeFormat.namedArguments(
        "id" to CodeBlock.of("%T(%N.%N)", KotlinRuntimeTypes.ELEMENT_ID, node.objectName, ELEMENT_ID),
        "elementType" to CodeBlock.of("%T.%L", KotlinRuntimeTypes.BPMN_ELEMENT_TYPE, node.elementType),
        "name" to node.name?.let { CodeBlock.of("%S", it) },
    )

    private fun followingType(node: FlowGraphNode): ClassName = when {
        node.branches -> KotlinFlowNodeType(node.objectName).successorMarker
        else -> KotlinFlowNodeType(node.successors.single()).typeName
    }

    // A bare `Start` in the supertype header would bind to an enclosing object's `Start`; qualify with the node.
    private fun ownHolderInterface(interfaceType: ClassName, node: FlowGraphNode, holderName: String): TypeName {
        val ownHolder = ClassName("", node.objectName, holderName)
        return interfaceType.parameterizedBy(ownHolder)
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent`. A single flow is created via `SequenceFlows.single`, several flows to the same target are listed.
     */
    private fun addSuccessors(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        val successors = node.successors.map { successor ->
            val flowsToTarget = node.outgoingFlows.find { it.target.objectName == successor.objectName }
            flowsToTarget?.let { sequenceFlowsCall(it) } ?: CodeBlock.of("%T(target = %N)", KotlinRuntimeTypes.ATTACHED_BOUNDARY_EVENT, successor.objectName)
        }
        val successorType = KotlinRuntimeTypes.SUCCESSOR.parameterizedBy(followingType(node))
        val getter = FunSpec.getterBuilder().addStatement("return listOf(⇥\n%L,⇤\n)", successors.joinToCode(",\n")).build()
        nodeBuilder.addProperty(PropertySpec.builder("outgoing", LIST.parameterizedBy(successorType), KModifier.OVERRIDE).getter(getter).build())
    }

    private fun sequenceFlowsCall(flowsToTarget: FlowsToTarget): CodeBlock {
        val targetName = flowsToTarget.target.objectName
        val singleFlow = flowsToTarget.flows.singleOrNull()
        if (singleFlow != null) {
            val arguments = KotlinCodeFormat.namedArguments(
                "flowId" to CodeBlock.of("%T(%S)", KotlinRuntimeTypes.ELEMENT_ID, singleFlow.id),
                "name" to singleFlow.name?.let { stringLiteral(it) },
                "conditionExpression" to singleFlow.conditionExpression?.let { stringLiteral(it) },
                "isDefault" to CodeBlock.of("%L", true).takeIf { singleFlow.isDefault },
                "target" to CodeBlock.of("%N", targetName),
            )
            return CodeBlock.of("%T.single(%L)", KotlinRuntimeTypes.SEQUENCE_FLOWS, arguments)
        }
        val flows = flowsToTarget.flows.map { sequenceFlowCall(it, targetName) }
        return CodeBlock.of("%T(⇥\ntarget = %N,\nflows = listOf(⇥\n%L,⇤\n),⇤\n)", KotlinRuntimeTypes.SEQUENCE_FLOWS, targetName, flows.joinToCode(",\n"))
    }

    private fun interiorStartsHolder(node: FlowGraphNode): TypeSpec? {
        if (node.interiorStarts.isEmpty()) return null
        val holderBuilder = TypeSpec.objectBuilder(START_HOLDER)
        node.interiorStarts.forEach { start -> holderBuilder.addProperty(nodeAccessor(start)) }
        return holderBuilder.build()
    }

    private fun accessorProperty(propertyName: String, holderName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", holderName))
        .addModifiers(KModifier.OVERRIDE)
        .initializer("%N", holderName).build()

    private fun nodeAccessor(node: FlowEdge): PropertySpec = PropertySpec.builder(node.propertyName, KotlinFlowNodeType(node).typeName)
        .getter(FunSpec.getterBuilder().addStatement("return %N", node.objectName).build()).build()

    private fun sequenceFlowCall(flow: SequenceFlowEdge, targetObjectName: String): CodeBlock = KotlinCodeFormat.namedCall(
        KotlinRuntimeTypes.SEQUENCE_FLOW,
        "id" to CodeBlock.of("%T(%S)", KotlinRuntimeTypes.ELEMENT_ID, flow.id),
        "name" to flow.name?.let { stringLiteral(it) },
        "conditionExpression" to flow.conditionExpression?.let { stringLiteral(it) },
        "isDefault" to CodeBlock.of("%L", true).takeIf { flow.isDefault },
        "target" to CodeBlock.of("%N", targetObjectName),
    )

    private companion object {
        private const val ELEMENT_ID = "ELEMENT_ID"
        private const val START_HOLDER = "Start"
    }
}
