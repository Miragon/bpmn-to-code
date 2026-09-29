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
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a Kotlin process API `FlowNodes` object: one nested node object per flow
 * node, carrying its metadata via `AbstractFlowNode`, its own facets (see [KotlinFacetWriter]), its reachable
 * successors behind `next` and its outgoing sequence flows behind `outgoingFlows`, named after the elements
 * they lead to. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a subprocess node
 * additionally is a `FlowScope` whose `startEvents` yields the interior's start elements, and a boundary event is
 * a `BoundaryEvent` of its host. Every node implements the flow's sealed `Node` interface, and `FlowNodes.entries` lists them all.
 */
internal class KotlinFlowWriter {

    private val facetWriter = KotlinFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        addEnumeration(builder, graph)
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun addEnumeration(builder: TypeSpec.Builder, graph: FlowGraph) {
        val nodeInterface = TypeSpec.interfaceBuilder(NODE_INTERFACE)
            .addModifiers(KModifier.SEALED)
            .addSuperinterface(KotlinRuntimeTypes.FLOW_NODE)
            .addKdoc("Common supertype of this flow's nodes, so a `when` over them can be exhaustive.").build()
        val entries = PropertySpec.builder("entries", LIST.parameterizedBy(ClassName("", NODE_INTERFACE)))
            .addKdoc("Every node of this flow, so tests can check all elements (job workers, deployed ids, …) without reflection.")
            .initializer(KotlinCodeFormat.listOfNames(graph.nodes.map { it.objectName })).build()
        builder.addType(nodeInterface).addProperty(entries)
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
        nodeBuilder.addProperty(PropertySpec.builder(ELEMENT_ID, String::class).addModifiers(KModifier.CONST).initializer("%L", stringLiteral(node.id)).build())
        facetWriter.properties(node.facets).forEach { nodeBuilder.addProperty(it) }
        facetWriter.holders(node.facets).forEach { nodeBuilder.addType(it) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(nodeBuilder, node)
        }
        if (node.outgoingFlows.isNotEmpty()) {
            addOutgoingFlows(nodeBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            addInteriorStarts(nodeBuilder, node)
        }
        return nodeBuilder.build()
    }

    private fun extendFlowNode(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.superclass(KotlinRuntimeTypes.ABSTRACT_FLOW_NODE)
            .addSuperclassConstructorParameter(superclassArguments(node))
            .addSuperinterface(ClassName("", NODE_INTERFACE))
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface(interfaceType = KotlinRuntimeTypes.HAS_SUCCESSORS, node = node, holderName = NEXT_HOLDER))
        }
        if (node.outgoingFlows.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface(interfaceType = KotlinRuntimeTypes.HAS_OUTGOING_FLOWS, node = node, holderName = OUTGOING_FLOWS_HOLDER))
        }
        val host = node.facets.attachedTo
        when {
            node.isBoundaryEvent && host != null ->
                nodeBuilder.addSuperinterface(KotlinRuntimeTypes.BOUNDARY_EVENT.parameterizedBy(ClassName("", host.objectName)))

            node.eventType != null -> nodeBuilder.addSuperinterface(KotlinRuntimeTypes.EVENT)
        }
        node.eventType?.let { eventType ->
            val eventTypeClass = KotlinRuntimeTypes.BPMN_EVENT_TYPE
            nodeBuilder.addProperty(PropertySpec.builder("eventType", eventTypeClass, KModifier.OVERRIDE).initializer("%T.%L", eventTypeClass, eventType).build())
        }
    }

    // One named argument per line, trailing comma included, as Kotlin style wants a multi-line call.
    private fun superclassArguments(node: FlowGraphNode): CodeBlock {
        val arguments = CodeBlock.builder()
            .add("⇥\nid = %T(%S),\nelementType = %T.%L,", KotlinRuntimeTypes.ELEMENT_ID, node.id, KotlinRuntimeTypes.BPMN_ELEMENT_TYPE, node.elementType)
        node.name?.let { arguments.add("\nname = %S,", it) }
        return arguments.add("⇤\n").build()
    }

    // A bare `Next` in the supertype header would bind to an enclosing object's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceType: ClassName, node: FlowGraphNode, holderName: String): TypeName {
        val ownHolder = ClassName("", node.objectName, holderName)
        return interfaceType.parameterizedBy(ownHolder)
    }

    private fun addSuccessors(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addProperty(accessorProperty("next", NEXT_HOLDER))
        nodeBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addOutgoingFlows(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addProperty(accessorProperty("outgoingFlows", OUTGOING_FLOWS_HOLDER))
        val holder = TypeSpec.objectBuilder(OUTGOING_FLOWS_HOLDER)
        node.outgoingFlows.forEach { holder.addProperty(outgoingFlowsProperty(it)) }
        nodeBuilder.addType(holder.build())
    }

    private fun addInteriorStarts(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addSuperinterface(ownHolderInterface(interfaceType = KotlinRuntimeTypes.FLOW_SCOPE, node = node, holderName = START_HOLDER))
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
        .getter(FunSpec.getterBuilder().addStatement("return %N", holderName).build()).build()

    private fun nodeAccessor(propertyName: String, objectName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", objectName))
        .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build()).build()

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become a `List<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun outgoingFlowsProperty(flowsToTarget: FlowsToTarget): PropertySpec {
        val sequenceFlowType = KotlinRuntimeTypes.SEQUENCE_FLOW.parameterizedBy(ClassName("", flowsToTarget.target.objectName))
        val calls = flowsToTarget.flows.map { sequenceFlowCall(it, flowsToTarget.target.objectName) }
        val (type, value) = when (calls.size) {
            1 -> sequenceFlowType to calls.single()
            else -> LIST.parameterizedBy(sequenceFlowType) to CodeBlock.of("listOf(⇥\n%L,⇤\n)", calls.joinToCode(",\n"))
        }
        val getter = FunSpec.getterBuilder().addStatement("return %L", value).build()
        return PropertySpec.builder(flowsToTarget.propertyName, type).getter(getter).build()
    }

    private fun sequenceFlowCall(flow: SequenceFlowEdge, targetObjectName: String): CodeBlock = KotlinCodeFormat.namedCall(
        KotlinRuntimeTypes.SEQUENCE_FLOW,
        "id" to CodeBlock.of("%T(%S)", KotlinRuntimeTypes.ELEMENT_ID, flow.id),
        "name" to nullableStringLiteral(flow.name),
        "conditionExpression" to nullableStringLiteral(flow.conditionExpression),
        "isDefault" to CodeBlock.of("%L", flow.isDefault),
        "target" to CodeBlock.of("%N", targetObjectName),
    )

    private companion object {
        private const val ELEMENT_ID = "ELEMENT_ID"
        private const val NEXT_HOLDER = "Next"
        private const val OUTGOING_FLOWS_HOLDER = "OutgoingFlows"
        private const val START_HOLDER = "Start"
        private const val NODE_INTERFACE = "Node"
    }
}
