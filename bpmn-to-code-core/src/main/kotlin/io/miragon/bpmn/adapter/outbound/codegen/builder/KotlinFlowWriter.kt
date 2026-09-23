package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode

/**
 * Emits the typed navigation graph of a Kotlin process API `Flow` object: one nested node object per flow
 * node, carrying its metadata via `AbstractFlowNode` and its reachable successors behind `then()`. A
 * subprocess node additionally is a `FlowScope`: its interior nodes are nested on it and `start()` yields the
 * interior's start elements.
 */
internal class KotlinFlowWriter {

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
        node.name?.let { nodeBuilder.addProperty(nameProperty(it)) }
        node.calledProcessId?.let { nodeBuilder.addProperty(calledProcessProperty(it)) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(nodeBuilder, node)
        }
        node.inner?.let { addInterior(nodeBuilder, node, it) }
        return nodeBuilder.build()
    }

    private fun extendFlowNode(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        val elementIdClass = ClassName(RUNTIME_PACKAGE, "ElementId")
        nodeBuilder.superclass(ClassName(RUNTIME_PACKAGE, "AbstractFlowNode"))
            .addSuperclassConstructorParameter("%T(%S)", elementIdClass, node.id)
            .addSuperclassConstructorParameter("%S", node.elementType)
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(hasSuccessorsType(node))
        }
    }

    // A bare `Next` in the supertype header would bind to an enclosing subprocess's `Next`; qualify with the node.
    private fun hasSuccessorsType(node: FlowGraphNode): TypeName {
        val ownNext = ClassName("", node.objectName, "Next")
        return ClassName(RUNTIME_PACKAGE, "HasSuccessors").parameterizedBy(ownNext)
    }

    private fun addSuccessors(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        nodeBuilder.addFunction(accessorFunction("then", NEXT_HOLDER))
        nodeBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addInterior(nodeBuilder: TypeSpec.Builder, node: FlowGraphNode, interior: FlowGraph) {
        val startNodes = interior.nodes.filter { it.isStart }
        if (startNodes.isNotEmpty()) {
            val ownStart = ClassName("", node.objectName, START_HOLDER)
            nodeBuilder.addSuperinterface(ClassName(RUNTIME_PACKAGE, "FlowScope").parameterizedBy(ownStart))
            nodeBuilder.addFunction(accessorFunction("start", START_HOLDER))
            nodeBuilder.addType(accessorHolder(START_HOLDER, startNodes.map { it.propertyName to it.objectName }))
        }
        interior.nodes.forEach { child -> nodeBuilder.addType(buildNode(child)) }
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

    private fun nameProperty(displayName: String): PropertySpec {
        val stringClass = ClassName("kotlin", "String")
        return PropertySpec.builder("name", stringClass).initializer("%S", displayName).build()
    }

    private fun calledProcessProperty(calledProcessId: String): PropertySpec {
        val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
        return PropertySpec.builder("calledProcess", processIdClass).initializer("ProcessId(%S)", calledProcessId).build()
    }

    private fun nodeAccessor(propertyName: String, objectName: String): PropertySpec = PropertySpec.builder(propertyName, ClassName("", objectName))
        .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build())
        .build()

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
        private const val NEXT_HOLDER = "Next"
        private const val START_HOLDER = "Start"
    }
}
