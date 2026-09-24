package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `Flow` class: one nested node class per flow node,
 * carrying its metadata via `AbstractFlowNode`, its own facets (see [JavaFacetWriter]), its reachable
 * successors behind `then()` and its outgoing sequence flows as typed edges behind `flows()`. All nodes are
 * direct children of `Flow`, whatever their subprocess depth; a subprocess class additionally is a
 * `FlowScope` whose `start()` yields the interior's start elements. `Flow` also exposes a static accessor
 * method per node, since a Java nested class has to be instantiated to be used as a value.
 */
internal class JavaFlowWriter {

    private val facetWriter = JavaFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, node.objectName, static = true)) }
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(node.objectName).addModifiers(PUBLIC, STATIC, FINAL)
        extendFlowNode(classBuilder, node)
        facetWriter.fields(node.facets).forEach { classBuilder.addField(it) }
        facetWriter.methods(node.facets).forEach { classBuilder.addMethod(it) }
        facetWriter.holders(node.facets).forEach { classBuilder.addType(it) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(classBuilder, node)
        }
        if (node.flows.isNotEmpty()) {
            addFlows(classBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            addInteriorStarts(classBuilder, node)
        }
        return classBuilder.build()
    }

    private fun extendFlowNode(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.superclass(ClassName.get(RUNTIME_PACKAGE, "AbstractFlowNode"))
        classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(PUBLIC).addStatement(superCall(node)).build())
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface("HasSuccessors", node, NEXT_HOLDER))
        }
        if (node.flows.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface("HasFlows", node, FLOWS_HOLDER))
        }
    }

    private fun superCall(node: FlowGraphNode): CodeBlock {
        val elementIdClass = ClassName.get(RUNTIME_PACKAGE, "ElementId")
        val superCall = CodeBlock.builder().add("super(new \$T(\$S), \$S", elementIdClass, node.id, node.elementType)
        node.name?.let { superCall.add(", \$S", it) }
        return superCall.add(")").build()
    }

    // A bare `Next` in the implements clause would bind to an enclosing class's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceName: String, node: FlowGraphNode, holderName: String): ParameterizedTypeName {
        val ownHolder = ClassName.get("", node.objectName, holderName)
        return ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, interfaceName), ownHolder)
    }

    private fun addSuccessors(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addMethod(accessorMethod("then", NEXT_HOLDER))
        classBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addFlows(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addMethod(accessorMethod("flows", FLOWS_HOLDER))
        val holder = TypeSpec.classBuilder(FLOWS_HOLDER).addModifiers(PUBLIC, STATIC, FINAL)
        node.flows.forEach { holder.addMethod(flowEdgeMethod(it)) }
        classBuilder.addType(holder.build())
    }

    private fun addInteriorStarts(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addSuperinterface(ownHolderInterface("FlowScope", node, START_HOLDER))
        classBuilder.addMethod(accessorMethod("start", START_HOLDER))
        classBuilder.addType(accessorHolder(START_HOLDER, node.interiorStarts.map { it.propertyName to it.objectName }))
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

    private fun nodeAccessor(methodName: String, returnObjectName: String, static: Boolean): MethodSpec {
        val returnType = ClassName.get("", returnObjectName)
        val methodBuilder = MethodSpec.methodBuilder(methodName).addModifiers(PUBLIC).returns(returnType)
            .addStatement("return new \$T()", returnType)
        if (static) {
            methodBuilder.addModifiers(STATIC)
        }
        return methodBuilder.build()
    }

    private fun flowEdgeMethod(edge: SequenceFlowEdge): MethodSpec {
        val sequenceFlowClass = ClassName.get(RUNTIME_PACKAGE, "SequenceFlow")
        val targetClass = ClassName.get("", edge.target.objectName)
        return MethodSpec.methodBuilder(edge.propertyName).addModifiers(PUBLIC)
            .returns(ParameterizedTypeName.get(sequenceFlowClass, targetClass))
            .addStatement(
                "return new \$T<>(new \$T(\$S), \$S, \$S, \$L, new \$T())",
                sequenceFlowClass,
                ClassName.get(RUNTIME_PACKAGE, "ElementId"),
                edge.id,
                edge.name,
                edge.conditionExpression,
                edge.isDefault,
                targetClass,
            )
            .build()
    }

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
        private const val NEXT_HOLDER = "Next"
        private const val FLOWS_HOLDER = "Flows"
        private const val START_HOLDER = "Start"
    }
}
