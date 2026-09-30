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
 * behind `next`, named after the elements they lead to: the `SequenceFlows` to an element, or an attached boundary
 * event. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a subprocess node
 * additionally is a `FlowScope` whose `startEvents` yields the interior's start elements, and a boundary event is
 * a `BoundaryEvent` of its host. `FlowNodes.entries` lists every node.
 */
internal class KotlinFlowWriter {

    private val facetWriter = KotlinFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        builder.addProperty(entries(graph))
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun entries(graph: FlowGraph): PropertySpec = PropertySpec.builder("entries", LIST.parameterizedBy(KotlinRuntimeTypes.FLOW_NODE))
        .initializer(KotlinCodeFormat.listOfNames(graph.nodes.map { it.objectName })).build()

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
        nodeBuilder.addProperty(PropertySpec.builder(ELEMENT_ID, String::class).addModifiers(KModifier.CONST).initializer("%L", stringLiteral(node.id)).build())
        facetWriter.properties(node.facets).forEach { nodeBuilder.addProperty(it) }
        facetWriter.holders(node.facets).forEach { nodeBuilder.addType(it) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(nodeBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            addInteriorStarts(nodeBuilder, node)
        }
        return nodeBuilder.build()
    }

    private fun extendFlowNode(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.superclass(KotlinRuntimeTypes.ABSTRACT_FLOW_NODE)
            .addSuperclassConstructorParameter(superclassArguments(node))
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface(interfaceType = KotlinRuntimeTypes.HAS_SUCCESSORS, node = node, holderName = NEXT_HOLDER))
        }
        val host = node.facets.attachedTo
        when {
            node.isBoundaryEvent && host != null ->
                nodeBuilder.addSuperinterface(KotlinRuntimeTypes.BOUNDARY_EVENT.parameterizedBy(ClassName("", host.objectName)))

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

    // A bare `Next` in the supertype header would bind to an enclosing object's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceType: ClassName, node: FlowGraphNode, holderName: String): TypeName {
        val ownHolder = ClassName("", node.objectName, holderName)
        return interfaceType.parameterizedBy(ownHolder)
    }

    private fun addSuccessors(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addProperty(accessorProperty("next", NEXT_HOLDER))
        val holder = TypeSpec.objectBuilder(NEXT_HOLDER)
        node.successors.forEach { successor -> holder.addProperty(successorProperty(successor, node.outgoingFlows.find { it.target.objectName == successor.objectName })) }
        nodeBuilder.addType(holder.build())
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent`. A single flow without name, condition or default marker uses the short `flowId` form.
     */
    private fun successorProperty(successor: FlowEdge, flowsToTarget: FlowsToTarget?): PropertySpec {
        val target = ClassName("", successor.objectName)
        val (successorType, value) = when (flowsToTarget) {
            null -> KotlinRuntimeTypes.ATTACHED_BOUNDARY_EVENT to CodeBlock.of("%T(target = %N)", KotlinRuntimeTypes.ATTACHED_BOUNDARY_EVENT, successor.objectName)
            else -> KotlinRuntimeTypes.SEQUENCE_FLOWS to sequenceFlowsCall(flowsToTarget)
        }
        val getter = FunSpec.getterBuilder().addStatement("return %L", value).build()
        return PropertySpec.builder(successor.propertyName, successorType.parameterizedBy(target)).getter(getter).build()
    }

    private fun sequenceFlowsCall(flowsToTarget: FlowsToTarget): CodeBlock {
        val targetName = flowsToTarget.target.objectName
        val plainFlow = flowsToTarget.flows.singleOrNull()?.takeIf { it.hasOnlyDefaults() }
        if (plainFlow != null) {
            return CodeBlock.of("%T(target = %N, flowId = %T(%S))", KotlinRuntimeTypes.SEQUENCE_FLOWS, targetName, KotlinRuntimeTypes.ELEMENT_ID, plainFlow.id)
        }
        val flows = flowsToTarget.flows.map { sequenceFlowCall(it, targetName) }
        return CodeBlock.of("%T(⇥\ntarget = %N,\nflows = listOf(⇥\n%L,⇤\n),⇤\n)", KotlinRuntimeTypes.SEQUENCE_FLOWS, targetName, flows.joinToCode(",\n"))
    }

    private fun addInteriorStarts(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addProperty(accessorProperty("startEvents", START_HOLDER))
        nodeBuilder.addType(accessorHolder(START_HOLDER, node.interiorStarts.map { it.propertyName to it.objectName }))
    }

    private fun accessorHolder(holderName: String, accessors: List<Pair<String, String>>): TypeSpec {
        val holderBuilder = TypeSpec.objectBuilder(holderName)
        accessors.forEach { (propertyName, objectName) -> holderBuilder.addProperty(nodeAccessor(propertyName, objectName)) }
        return holderBuilder.build()
    }

    private fun accessorProperty(propertyName: String, holderName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", holderName))
        .addModifiers(KModifier.OVERRIDE)
        .initializer("%N", holderName).build()

    private fun nodeAccessor(propertyName: String, objectName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", objectName))
        .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build()).build()

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
        private const val NEXT_HOLDER = "Next"
        private const val START_HOLDER = "Start"
    }
}
