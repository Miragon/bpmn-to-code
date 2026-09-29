package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowsToTarget
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `Flow` class: one nested node class per flow node,
 * carrying its metadata via `AbstractFlowNode`, its own facets (see [JavaFacetWriter]), its reachable
 * successors behind `getNext()` and its outgoing sequence flows behind `getOutgoingFlows()`, named after the
 * elements they lead to. All nodes are direct children of `Flow`, whatever their subprocess depth; a subprocess
 * class additionally is a `FlowScope` whose `getStartEvents()` yields the interior's start elements, and a boundary event
 * is a `BoundaryEvent` of its host. `Flow` also exposes a static accessor
 * method per node, since a Java nested class has to be instantiated to be used as a value, and `all()` listing
 * every node.
 */
internal class JavaFlowWriter {

    private val facetWriter = JavaFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        builder.addMethod(allNodes(graph))
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, node.objectName, static = true)) }
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun allNodes(graph: FlowGraph): MethodSpec {
        val nodes = graph.nodes.map { CodeBlock.of("new \$T()", ClassName.get("", it.objectName)) }
        return MethodSpec.methodBuilder("all").addModifiers(PUBLIC, STATIC)
            .addJavadoc("Every node of this flow, so tests can check all elements (job workers, deployed ids, …) without reflection.\n")
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), ClassName.get(RUNTIME_PACKAGE, "FlowNode")))
            .addStatement("return \$T.of(\n\$L)", List::class.java, CodeBlock.join(nodes, ",\n"))
            .build()
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
        if (node.outgoingFlows.isNotEmpty()) {
            addOutgoingFlows(classBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            addInteriorStarts(classBuilder, node)
        }
        return classBuilder.build()
    }

    private fun extendFlowNode(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.superclass(ClassName.get(RUNTIME_PACKAGE, "AbstractFlowNode"))
        classBuilder.addField(FieldSpec.builder(String::class.java, ELEMENT_ID, PUBLIC, STATIC, FINAL).initializer("\$S", node.id).build())
        classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(PUBLIC).addStatement(superCall(node)).build())
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface("HasSuccessors", node, NEXT_HOLDER))
        }
        if (node.outgoingFlows.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface("HasOutgoingFlows", node, OUTGOING_FLOWS_HOLDER))
        }
        val host = node.facets.attachedTo
        if (node.isBoundaryEvent && host != null) {
            classBuilder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "BoundaryEvent"), ClassName.get("", host.objectName)))
        }
    }

    private fun superCall(node: FlowGraphNode): CodeBlock {
        val elementIdClass = ClassName.get(RUNTIME_PACKAGE, "ElementId")
        val superCall = CodeBlock.builder().add("super(new \$T(\$N), \$S", elementIdClass, ELEMENT_ID, node.elementType)
        node.name?.let { superCall.add(", \$S", it) }
        return superCall.add(")").build()
    }

    // A bare `Next` in the implements clause would bind to an enclosing class's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceName: String, node: FlowGraphNode, holderName: String): ParameterizedTypeName {
        val ownHolder = ClassName.get("", node.objectName, holderName)
        return ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, interfaceName), ownHolder)
    }

    private fun addSuccessors(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addMethod(accessorMethod("getNext", NEXT_HOLDER))
        classBuilder.addType(accessorHolder(NEXT_HOLDER, node.successors.map { it.propertyName to it.objectName }))
    }

    private fun addOutgoingFlows(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addMethod(accessorMethod("getOutgoingFlows", OUTGOING_FLOWS_HOLDER))
        val holder = TypeSpec.classBuilder(OUTGOING_FLOWS_HOLDER).addModifiers(PUBLIC, STATIC, FINAL)
        node.outgoingFlows.forEach { holder.addMethod(outgoingFlowsMethod(it)) }
        classBuilder.addType(holder.build())
    }

    private fun addInteriorStarts(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.addSuperinterface(ownHolderInterface("FlowScope", node, START_HOLDER))
        classBuilder.addMethod(accessorMethod("getStartEvents", START_HOLDER))
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

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become a `List<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun outgoingFlowsMethod(flowsToTarget: FlowsToTarget): MethodSpec {
        val targetClass = ClassName.get("", flowsToTarget.target.objectName)
        val sequenceFlowType = ParameterizedTypeName.get(ClassName.get(RUNTIME_PACKAGE, "SequenceFlow"), targetClass)
        val constructions = flowsToTarget.flows.map { sequenceFlowConstruction(it, targetClass) }
        val method = MethodSpec.methodBuilder(flowsToTarget.propertyName).addModifiers(PUBLIC)
        return when (constructions.size) {
            1 -> method.returns(sequenceFlowType).addStatement("return \$L", constructions.single())

            else -> method.returns(ParameterizedTypeName.get(ClassName.get(List::class.java), sequenceFlowType))
                .addStatement("return \$T.of(\$L)", List::class.java, CodeBlock.join(constructions, ", "))
        }.build()
    }

    private fun sequenceFlowConstruction(flow: SequenceFlowEdge, targetClass: ClassName): CodeBlock = CodeBlock.of(
        "new \$T<>(new \$T(\$S), \$S, \$S, \$L, new \$T())",
        ClassName.get(RUNTIME_PACKAGE, "SequenceFlow"),
        ClassName.get(RUNTIME_PACKAGE, "ElementId"),
        flow.id,
        flow.name,
        flow.conditionExpression,
        flow.isDefault,
        targetClass,
    )

    private companion object {
        private const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
        private const val ELEMENT_ID = "ELEMENT_ID"
        private const val NEXT_HOLDER = "Next"
        private const val OUTGOING_FLOWS_HOLDER = "OutgoingFlows"
        private const val START_HOLDER = "Start"
    }
}
