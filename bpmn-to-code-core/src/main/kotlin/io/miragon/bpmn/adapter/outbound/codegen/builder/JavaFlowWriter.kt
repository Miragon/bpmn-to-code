package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `Flow` class: one nested node class per flow node,
 * carrying its metadata via `AbstractFlowNode` and its reachable successors behind `then()`. A subprocess
 * class additionally is a `FlowScope`: its interior nodes are nested on it and `start()` yields the interior's
 * start elements. Every scope also exposes an accessor method per node, since a Java nested class has to be
 * instantiated to be used as a value.
 */
internal class JavaFlowWriter {

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, node.objectName, static = true)) }
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(node.objectName).addModifiers(PUBLIC, STATIC, FINAL)
        extendFlowNode(classBuilder, node)
        node.name?.let { classBuilder.addField(nameField(it)) }
        node.calledProcessId?.let { classBuilder.addField(calledProcessField(it)) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(classBuilder, node)
        }
        node.inner?.let { addInterior(classBuilder, node, it) }
        return classBuilder.build()
    }

    private fun extendFlowNode(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        val elementIdClass = ClassName.get(RUNTIME_PACKAGE, "ElementId")
        classBuilder.superclass(ClassName.get(RUNTIME_PACKAGE, "AbstractFlowNode"))
        classBuilder.addMethod(
            MethodSpec.constructorBuilder().addModifiers(PUBLIC)
                .addStatement("super(new \$T(\$S), \$S)", elementIdClass, node.id, node.elementType).build(),
        )
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(hasSuccessorsType(node))
        }
    }

    // A bare `Next` in the implements clause would bind to an enclosing subprocess's `Next`; qualify with the node.
    private fun hasSuccessorsType(node: FlowGraphNode): ParameterizedTypeName {
        val ownNext = ClassName.get("", node.objectName, NEXT_HOLDER)
        return ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "HasSuccessors"), ownNext)
    }

    private fun addSuccessors(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addMethod(accessorMethod("then", NEXT_HOLDER))
        classBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addInterior(classBuilder: TypeSpec.Builder, node: FlowGraphNode, interior: FlowGraph) {
        val startNodes = interior.nodes.filter { it.isStart }
        if (startNodes.isNotEmpty()) {
            val ownStart = ClassName.get("", node.objectName, START_HOLDER)
            classBuilder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "FlowScope"), ownStart))
            classBuilder.addMethod(accessorMethod("start", START_HOLDER))
            classBuilder.addType(accessorHolder(START_HOLDER, startNodes.map { it.propertyName to it.objectName }))
        }
        interior.nodes.forEach { child -> classBuilder.addMethod(nodeAccessor(child.propertyName, child.objectName, static = false)) }
        interior.nodes.forEach { child -> classBuilder.addType(buildNode(child)) }
    }

    private fun accessorHolder(holderName: String, accessors: List<Pair<String, String>>): TypeSpec {
        val holderBuilder = TypeSpec.classBuilder(holderName).addModifiers(PUBLIC, STATIC, FINAL)
        accessors.forEach { (propertyName, objectName) -> holderBuilder.addMethod(nodeAccessor(propertyName, objectName, static = false)) }
        return holderBuilder.build()
    }

    private fun accessorMethod(methodName: String, holderName: String): MethodSpec {
        val holderClass = ClassName.get("", holderName)
        return MethodSpec.methodBuilder(methodName).addAnnotation(Override::class.java).addModifiers(PUBLIC).returns(holderClass)
            .addStatement("return new \$T()", holderClass).build()
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
        private const val NEXT_HOLDER = "Next"
        private const val START_HOLDER = "Start"
    }
}
