package io.miragon.bpmn.adapter.outbound.codegen.builder

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
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a Kotlin process API `Flow` object: one nested node object per flow
 * node, carrying its metadata via `AbstractFlowNode`, its own facets (see [KotlinFacetWriter]), its reachable
 * successors behind `then()` and its outgoing sequence flows behind `outgoingFlows()`, named after the elements
 * they lead to. All nodes are direct children of `Flow`, whatever their subprocess depth; a subprocess node
 * additionally is a `FlowScope` whose `start()` yields the interior's start elements, and a boundary event is
 * marked `BoundaryEvent`.
 */
internal class KotlinFlowWriter {

    private val facetWriter = KotlinFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
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
        nodeBuilder.superclass(ClassName(RUNTIME_PACKAGE, "AbstractFlowNode"))
            .addSuperclassConstructorParameter(superclassArguments(node))
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface("HasSuccessors", node, NEXT_HOLDER))
        }
        if (node.outgoingFlows.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface("HasOutgoingFlows", node, OUTGOING_FLOWS_HOLDER))
        }
        if (node.isBoundaryEvent) {
            nodeBuilder.addSuperinterface(ClassName(RUNTIME_PACKAGE, "BoundaryEvent"))
        }
    }

    // One named argument per line, trailing comma included, as Kotlin style wants a multi-line call.
    private fun superclassArguments(node: FlowGraphNode): CodeBlock {
        val arguments = CodeBlock.builder()
            .add("⇥\nid = %T(%S),\nelementType = %S,", ClassName(RUNTIME_PACKAGE, "ElementId"), node.id, node.elementType)
        node.name?.let { arguments.add("\nname = %S,", it) }
        return arguments.add("⇤\n").build()
    }

    // A bare `Next` in the supertype header would bind to an enclosing object's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceName: String, node: FlowGraphNode, holderName: String): TypeName {
        val ownHolder = ClassName("", node.objectName, holderName)
        return ClassName(RUNTIME_PACKAGE, interfaceName).parameterizedBy(ownHolder)
    }

    private fun addSuccessors(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addFunction(accessorFunction("then", NEXT_HOLDER))
        nodeBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addOutgoingFlows(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addFunction(accessorFunction("outgoingFlows", OUTGOING_FLOWS_HOLDER))
        val holder = TypeSpec.objectBuilder(OUTGOING_FLOWS_HOLDER)
        node.outgoingFlows.forEach { holder.addProperty(outgoingFlowsProperty(it)) }
        nodeBuilder.addType(holder.build())
    }

    private fun addInteriorStarts(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addSuperinterface(ownHolderInterface("FlowScope", node, START_HOLDER))
        nodeBuilder.addFunction(accessorFunction("start", START_HOLDER))
        nodeBuilder.addType(accessorHolder(START_HOLDER, node.interiorStarts.map { it.propertyName to it.objectName }))
    }

    private fun accessorHolder(holderName: String, accessors: List<Pair<String, String>>): TypeSpec {
        val holderBuilder = TypeSpec.objectBuilder(holderName)
        accessors.forEach { (propertyName, objectName) -> holderBuilder.addProperty(nodeAccessor(propertyName, objectName)) }
        return holderBuilder.build()
    }

    private fun accessorFunction(functionName: String, holderName: String): FunSpec = FunSpec.builder(functionName)
        .addModifiers(KModifier.OVERRIDE)
        .returns(ClassName("", holderName))
        .addStatement("return %N", holderName)
        .build()

    private fun nodeAccessor(propertyName: String, objectName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", objectName))
        .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build())
        .build()

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become a `List<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun outgoingFlowsProperty(flowsToTarget: FlowsToTarget): PropertySpec {
        val sequenceFlowType = ClassName(RUNTIME_PACKAGE, "SequenceFlow").parameterizedBy(ClassName("", flowsToTarget.target.objectName))
        val calls = flowsToTarget.flows.map { sequenceFlowCall(it, flowsToTarget.target.objectName) }
        val (type, value) = when (calls.size) {
            1 -> sequenceFlowType to calls.single()
            else -> LIST.parameterizedBy(sequenceFlowType) to CodeBlock.of("listOf(⇥\n%L,⇤\n)", calls.joinToCode(",\n"))
        }
        val getter = FunSpec.getterBuilder().addStatement("return %L", value).build()
        return PropertySpec.builder(flowsToTarget.propertyName, type).getter(getter).build()
    }

    private fun sequenceFlowCall(flow: SequenceFlowEdge, targetObjectName: String): CodeBlock = kotlinNamedCall(
        ClassName(RUNTIME_PACKAGE, "SequenceFlow"),
        "id" to CodeBlock.of("%T(%S)", ClassName(RUNTIME_PACKAGE, "ElementId"), flow.id),
        "name" to kotlinNullableStringLiteral(flow.name),
        "conditionExpression" to kotlinNullableStringLiteral(flow.conditionExpression),
        "isDefault" to CodeBlock.of("%L", flow.isDefault),
        "target" to CodeBlock.of("%N", targetObjectName),
    )

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
        private const val NEXT_HOLDER = "Next"
        private const val OUTGOING_FLOWS_HOLDER = "OutgoingFlows"
        private const val START_HOLDER = "Start"
    }
}
