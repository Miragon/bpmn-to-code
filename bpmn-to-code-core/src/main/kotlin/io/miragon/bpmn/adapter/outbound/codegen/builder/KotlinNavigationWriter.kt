package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph.NavigationNode

/**
 * Emits the typed navigation graph of a Kotlin process API `Relations` object: one nested node object per
 * flow node, carrying its metadata via `AbstractFlowNode` and its reachable successors behind `then()`. A
 * subprocess's interior is written recursively as its nested `Inner` scope.
 */
internal class KotlinNavigationWriter {

    fun write(builder: TypeSpec.Builder, graph: NavigationGraph, navigable: Boolean = false) {
        graph.nodes.forEach { node -> builder.addProperty(nodeAccessor(node.propertyName, node.objectName)) }
        addScopeStart(builder, graph.nodes.filter { it.isStart }, navigable)
        graph.nodes.forEach { node -> builder.addType(buildNavigationNode(node)) }
    }

    private fun addScopeStart(builder: TypeSpec.Builder, startNodes: List<NavigationNode>, navigable: Boolean) {
        if (startNodes.isEmpty()) {
            return
        }
        builder.addFunction(thenFunction(override = navigable))
        builder.addType(nextHolder(startNodes.map { it.propertyName to it.objectName }))
    }

    private fun buildNavigationNode(node: NavigationNode): TypeSpec {
        val nodeBuilder = TypeSpec.objectBuilder(node.objectName)
        extendFlowNode(nodeBuilder, node)
        node.name?.let { nodeBuilder.addProperty(nameProperty(it)) }
        node.calledProcessId?.let { nodeBuilder.addProperty(calledProcessProperty(it)) }
        if (node.successors.isNotEmpty()) {
            addTransitions(nodeBuilder, node)
        }
        node.inner?.let { addInnerScope(nodeBuilder, node, it) }
        return nodeBuilder.build()
    }

    private fun extendFlowNode(nodeBuilder: TypeSpec.Builder, node: NavigationNode) {
        val elementIdClass = ClassName(RUNTIME_PACKAGE, "ElementId")
        nodeBuilder.superclass(ClassName(RUNTIME_PACKAGE, "AbstractFlowNode"))
            .addSuperclassConstructorParameter("%T(%S)", elementIdClass, node.id)
            .addSuperclassConstructorParameter("%S", node.elementType)
        if (node.successors.isNotEmpty()) {
            nodeBuilder.addSuperinterface(navigableType(node))
        }
    }

    // A bare `Next` in the supertype header would bind to the scope's `Relations.Next`; qualify with the node.
    private fun navigableType(node: NavigationNode): TypeName {
        val ownNext = ClassName("", node.objectName, "Next")
        return ClassName(RUNTIME_PACKAGE, "Navigable").parameterizedBy(ownNext)
    }

    private fun addTransitions(nodeBuilder: TypeSpec.Builder, node: NavigationNode) {
        nodeBuilder.addFunction(thenFunction(override = true))
        nodeBuilder.addType(nextHolder(node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addInnerScope(nodeBuilder: TypeSpec.Builder, node: NavigationNode, inner: NavigationGraph) {
        val innerNavigable = inner.nodes.any { it.isStart }
        // Qualify with the node so a bare `Inner`/`Next` doesn't bind to an enclosing scope's type.
        nodeBuilder.addSuperinterface(
            ClassName(RUNTIME_PACKAGE, "HasInner").parameterizedBy(ClassName("", node.objectName, "Inner"))
        )
        nodeBuilder.addFunction(innerFunction(node))
        val innerBuilder = TypeSpec.objectBuilder("Inner")
        if (innerNavigable) {
            val innerNext = ClassName("", node.objectName, "Inner", "Next")
            innerBuilder.addSuperinterface(ClassName(RUNTIME_PACKAGE, "NavigationScope").parameterizedBy(innerNext))
        }
        write(innerBuilder, inner, navigable = innerNavigable)
        nodeBuilder.addType(innerBuilder.build())
    }

    private fun innerFunction(node: NavigationNode): FunSpec {
        val innerType = ClassName("", node.objectName, "Inner")
        return FunSpec.builder("inner").addModifiers(KModifier.OVERRIDE).returns(innerType)
            .addStatement("return %T", innerType).build()
    }

    private fun nextHolder(accessors: List<Pair<String, String>>): TypeSpec {
        val nextBuilder = TypeSpec.objectBuilder("Next")
        accessors.forEach { (propertyName, objectName) -> nextBuilder.addProperty(nodeAccessor(propertyName, objectName)) }
        return nextBuilder.build()
    }

    private fun thenFunction(override: Boolean): FunSpec {
        val builder = FunSpec.builder("then").returns(ClassName("", "Next")).addStatement("return %N", "Next")
        if (override) {
            builder.addModifiers(KModifier.OVERRIDE)
        }
        return builder.build()
    }

    private fun nameProperty(displayName: String): PropertySpec {
        val stringClass = ClassName("kotlin", "String")
        return PropertySpec.builder("name", stringClass).initializer("%S", displayName).build()
    }

    private fun calledProcessProperty(calledProcessId: String): PropertySpec {
        val processIdClass = ClassName(RUNTIME_PACKAGE, "ProcessId")
        return PropertySpec.builder("calledProcess", processIdClass).initializer("ProcessId(%S)", calledProcessId).build()
    }

    private fun nodeAccessor(propertyName: String, objectName: String): PropertySpec {
        return PropertySpec.builder(propertyName, ClassName("", objectName))
            .getter(FunSpec.getterBuilder().addStatement("return %N", objectName).build())
            .build()
    }

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }
}
