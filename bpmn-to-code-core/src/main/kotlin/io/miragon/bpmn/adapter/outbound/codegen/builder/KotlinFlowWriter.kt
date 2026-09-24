package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge

/**
 * Emits the typed navigation graph of a Kotlin process API `Flow` object: one nested node object per flow
 * node, carrying its metadata via `AbstractFlowNode`, its own facets (see [KotlinFacetWriter]), its reachable
 * successors behind `then()` and its outgoing sequence flows as typed edges behind `flows()`. All nodes are
 * direct children of `Flow`, whatever their subprocess depth; a subprocess node additionally is a `FlowScope`
 * whose `start()` yields the interior's start elements.
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
        if (node.flows.isNotEmpty()) {
            addFlows(nodeBuilder, node)
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
        if (node.flows.isNotEmpty()) {
            nodeBuilder.addSuperinterface(ownHolderInterface("HasFlows", node, FLOWS_HOLDER))
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

    private fun addFlows(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addFunction(accessorFunction("flows", FLOWS_HOLDER))
        val holder = TypeSpec.objectBuilder(FLOWS_HOLDER)
        node.flows.forEach { holder.addProperty(flowEdgeProperty(it)) }
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

    private fun flowEdgeProperty(edge: SequenceFlowEdge): PropertySpec {
        val sequenceFlowClass = ClassName(RUNTIME_PACKAGE, "SequenceFlow")
        val targetClass = ClassName("", edge.target.objectName)
        val call = kotlinNamedCall(
            sequenceFlowClass,
            "id" to CodeBlock.of("%T(%S)", ClassName(RUNTIME_PACKAGE, "ElementId"), edge.id),
            "name" to kotlinNullableStringLiteral(edge.name),
            "conditionExpression" to kotlinNullableStringLiteral(edge.conditionExpression),
            "isDefault" to CodeBlock.of("%L", edge.isDefault),
            "target" to CodeBlock.of("%N", edge.target.objectName),
        )
        val getter = FunSpec.getterBuilder().addStatement("return %L", call).build()
        return PropertySpec.builder(edge.propertyName, sequenceFlowClass.parameterizedBy(targetClass)).getter(getter).build()
    }

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
        private const val NEXT_HOLDER = "Next"
        private const val FLOWS_HOLDER = "Flows"
        private const val START_HOLDER = "Start"
    }
}
