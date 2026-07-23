package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph
import io.miragon.bpmn.adapter.outbound.codegen.navigation.NavigationGraph.NavigationNode
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `Relations` class: one nested node class per flow
 * node, carrying its metadata via [AbstractFlowNode][RUNTIME_PACKAGE] and its reachable successors behind
 * `then()`. A subprocess's interior is written recursively as its nested `Inner` scope.
 */
internal class JavaNavigationWriter {

    fun write(builder: TypeSpec.Builder, graph: NavigationGraph, staticAccessors: Boolean, navigable: Boolean = false) {
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, node.objectName, staticAccessors)) }
        addScopeStart(builder, graph.nodes.filter { it.isStart }, staticAccessors, navigable)
        graph.nodes.forEach { node -> builder.addType(buildNavigationNode(node)) }
    }

    private fun addScopeStart(builder: TypeSpec.Builder, startNodes: List<NavigationNode>, staticAccessors: Boolean, navigable: Boolean) {
        if (startNodes.isEmpty()) {
            return
        }
        builder.addMethod(thenMethod(static = staticAccessors, override = navigable))
        builder.addType(nextHolder(startNodes.map { it.propertyName to it.objectName }))
    }

    private fun buildNavigationNode(node: NavigationNode): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(node.objectName).addModifiers(PUBLIC, STATIC, FINAL)
        extendFlowNode(classBuilder, node)
        node.name?.let { classBuilder.addField(nameField(it)) }
        node.calledProcessId?.let { classBuilder.addField(calledProcessField(it)) }
        if (node.successors.isNotEmpty()) {
            addTransitions(classBuilder, node)
        }
        node.inner?.let { addInnerScope(classBuilder, node, it) }
        return classBuilder.build()
    }

    private fun extendFlowNode(classBuilder: TypeSpec.Builder, node: NavigationNode) {
        val elementIdClass = ClassName.get(RUNTIME_PACKAGE, "ElementId")
        classBuilder.superclass(ClassName.get(RUNTIME_PACKAGE, "AbstractFlowNode"))
        classBuilder.addMethod(
            MethodSpec.constructorBuilder().addModifiers(PUBLIC)
                .addStatement("super(new \$T(\$S), \$S)", elementIdClass, node.id, node.elementType).build()
        )
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(navigableType(node))
        }
    }

    // A bare `Next` in the implements clause would bind to the scope's `Relations.Next`; qualify with the node.
    private fun navigableType(node: NavigationNode): ParameterizedTypeName {
        val ownNext = ClassName.get("", node.objectName, "Next")
        return ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "Navigable"), ownNext)
    }

    private fun addTransitions(classBuilder: TypeSpec.Builder, node: NavigationNode) {
        classBuilder.addMethod(thenMethod(static = false, override = true))
        classBuilder.addType(nextHolder(node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addInnerScope(classBuilder: TypeSpec.Builder, node: NavigationNode, inner: NavigationGraph) {
        val innerNavigable = inner.nodes.any { it.isStart }
        // Qualify with the node so a bare `Inner`/`Next` doesn't bind to an enclosing scope's type.
        classBuilder.addSuperinterface(
            ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "HasInner"), ClassName.get("", node.objectName, "Inner"))
        )
        classBuilder.addMethod(innerMethod(node))
        val innerBuilder = TypeSpec.classBuilder("Inner").addModifiers(PUBLIC, STATIC, FINAL)
        if (innerNavigable) {
            val innerNext = ClassName.get("", node.objectName, "Inner", "Next")
            innerBuilder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "NavigationScope"), innerNext))
        }
        write(innerBuilder, inner, staticAccessors = false, navigable = innerNavigable)
        classBuilder.addType(innerBuilder.build())
    }

    private fun innerMethod(node: NavigationNode): MethodSpec {
        val innerType = ClassName.get("", node.objectName, "Inner")
        return MethodSpec.methodBuilder("inner").addAnnotation(Override::class.java).addModifiers(PUBLIC).returns(innerType)
            .addStatement("return new \$T()", innerType).build()
    }

    private fun nextHolder(accessors: List<Pair<String, String>>): TypeSpec {
        val nextBuilder = TypeSpec.classBuilder("Next").addModifiers(PUBLIC, STATIC, FINAL)
        accessors.forEach { (propertyName, objectName) -> nextBuilder.addMethod(nodeAccessor(propertyName, objectName, static = false)) }
        return nextBuilder.build()
    }

    private fun thenMethod(static: Boolean, override: Boolean = false): MethodSpec {
        val nextClass = ClassName.get("", "Next")
        val methodBuilder = MethodSpec.methodBuilder("then").addModifiers(PUBLIC).returns(nextClass)
            .addStatement("return new \$T()", nextClass)
        if (override) {
            methodBuilder.addAnnotation(Override::class.java)
        }
        if (static) {
            methodBuilder.addModifiers(STATIC)
        }
        return methodBuilder.build()
    }

    private fun nameField(displayName: String): FieldSpec {
        val stringClass = ClassName.get("java.lang", "String")
        return FieldSpec.builder(stringClass, "name", PUBLIC, FINAL).initializer("\$S", displayName).build()
    }

    private fun calledProcessField(calledProcessId: String): FieldSpec {
        val processIdClass = ClassName.get(RUNTIME_PACKAGE, "ProcessId")
        return FieldSpec.builder(processIdClass, "calledProcess", PUBLIC, FINAL)
            .initializer("new \$T(\$S)", processIdClass, calledProcessId).build()
    }

    private fun nodeAccessor(methodName: String, returnObjectName: String, static: Boolean): MethodSpec {
        val returnType = ClassName.get("", returnObjectName)
        val methodBuilder = MethodSpec.methodBuilder(methodName).addModifiers(PUBLIC).returns(returnType)
            .addStatement("return new \$T()", returnType)
        if (static) {
            methodBuilder.addModifiers(STATIC)
        }
        return methodBuilder.build()
    }

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }
}
