package io.miragon.bpmn.adapter.outbound.codegen.builder.java

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
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `FlowNodes` class: one nested node class per flow node,
 * carrying its metadata via `AbstractFlowNode`, its own facets (see [JavaFacetWriter]), its reachable
 * successors behind `getNext()` and its outgoing sequence flows behind `getOutgoingFlows()`, named after the
 * elements they lead to. All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a subprocess
 * class additionally is a `FlowScope` whose `getStartEvents()` yields the interior's start elements, and a boundary event
 * is a `BoundaryEvent` of its host. Every node is a singleton (see [JavaFlowNodeType]), and `FlowNodes` exposes a
 * static accessor method per node and `all()` listing every node.
 */
internal class JavaFlowWriter {

    private val facetWriter = JavaFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, node.objectName, PUBLIC, STATIC)) }
        builder.addMethod(allNodes(graph))
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
    }

    private fun allNodes(graph: FlowGraph): MethodSpec {
        val nodes = graph.nodes.map { JavaFlowNodeType(it.objectName).instance() }
        return MethodSpec.methodBuilder("all").addModifiers(PUBLIC, STATIC)
            .addJavadoc("Every node of this flow.\n")
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), JavaRuntimeTypes.FLOW_NODE))
            .addStatement($$"return $T.of(\n$L)", List::class.java, CodeBlock.join(nodes, ",\n")).build()
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
        classBuilder.superclass(JavaRuntimeTypes.ABSTRACT_FLOW_NODE)
        classBuilder.addField(FieldSpec.builder(String::class.java, ELEMENT_ID, PUBLIC, STATIC, FINAL).initializer($$"$S", node.id).build())
        classBuilder.addField(JavaFlowNodeType(node.objectName).instanceField())
        classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).addStatement(superCall(node)).build())
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface(interfaceType = JavaRuntimeTypes.HAS_SUCCESSORS, node = node, holderName = NEXT_HOLDER))
        }
        if (node.outgoingFlows.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface(interfaceType = JavaRuntimeTypes.HAS_OUTGOING_FLOWS, node = node, holderName = OUTGOING_FLOWS_HOLDER))
        }
        val host = node.facets.attachedTo
        when {
            node.isBoundaryEvent && host != null ->
                classBuilder.addSuperinterface(ParameterizedTypeName.get(JavaRuntimeTypes.BOUNDARY_EVENT, ClassName.get("", host.objectName)))

            node.eventType != null -> classBuilder.addSuperinterface(JavaRuntimeTypes.EVENT)
        }
        if (node.interiorStarts.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface(interfaceType = JavaRuntimeTypes.FLOW_SCOPE, node = node, holderName = START_HOLDER))
        }
        node.eventType?.let { eventType ->
            val eventTypeClass = JavaRuntimeTypes.BPMN_EVENT_TYPE
            classBuilder.addMethod(
                MethodSpec.methodBuilder("getEventType").addAnnotation(Override::class.java).addModifiers(PUBLIC).returns(eventTypeClass)
                    .addStatement($$"return $T.$L", eventTypeClass, eventType).build(),
            )
        }
    }

    private fun superCall(node: FlowGraphNode): CodeBlock {
        val elementIdClass = JavaRuntimeTypes.ELEMENT_ID
        val elementTypeClass = JavaRuntimeTypes.BPMN_ELEMENT_TYPE
        val superCall = CodeBlock.builder().add($$"super(new $T($N), $T.$L", elementIdClass, ELEMENT_ID, elementTypeClass, node.elementType)
        node.name?.let { superCall.add($$", $S", it) }
        return superCall.add(")").build()
    }

    // A bare `Next` in the implements clause would bind to an enclosing class's `Next`; qualify with the node.
    private fun ownHolderInterface(interfaceType: ClassName, node: FlowGraphNode, holderName: String): ParameterizedTypeName {
        val ownHolder = ClassName.get("", node.objectName, holderName)
        return ParameterizedTypeName.get(interfaceType, ownHolder)
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
        classBuilder.addMethod(accessorMethod("getStartEvents", START_HOLDER))
        classBuilder.addType(accessorHolder(START_HOLDER, node.interiorStarts.map { it.propertyName to it.objectName }))
    }

    private fun accessorHolder(holderName: String, accessors: List<Pair<String, String>>): TypeSpec {
        val holderBuilder = TypeSpec.classBuilder(holderName).addModifiers(PUBLIC, STATIC, FINAL)
        accessors.forEach { (propertyName, objectName) -> holderBuilder.addMethod(nodeAccessor(propertyName, objectName, PUBLIC)) }
        return holderBuilder.build()
    }

    private fun accessorMethod(methodName: String, holderName: String): MethodSpec {
        val holderClass = ClassName.get("", holderName)
        return MethodSpec.methodBuilder(methodName).addAnnotation(Override::class.java).addModifiers(PUBLIC).returns(holderClass)
            .addStatement($$"return new $T()", holderClass).build()
    }

    private fun nodeAccessor(methodName: String, returnObjectName: String, vararg modifiers: Modifier): MethodSpec {
        val returnNode = JavaFlowNodeType(returnObjectName)
        return MethodSpec.methodBuilder(methodName).addModifiers(*modifiers).returns(returnNode.className)
            .addStatement($$"return $L", returnNode.instance()).build()
    }

    /**
     * A single flow to the target is a `SequenceFlow<Target>`; several flows to the same target keep the name and
     * become a `List<SequenceFlow<Target>>`, so no flow is lost and no sibling is renamed.
     */
    private fun outgoingFlowsMethod(flowsToTarget: FlowsToTarget): MethodSpec {
        val target = JavaFlowNodeType(flowsToTarget.target.objectName)
        val sequenceFlowType = ParameterizedTypeName.get(JavaRuntimeTypes.SEQUENCE_FLOW, target.className)
        val constructions = flowsToTarget.flows.map { sequenceFlowConstruction(it, target) }
        val method = MethodSpec.methodBuilder(flowsToTarget.propertyName).addModifiers(PUBLIC)
        return when (constructions.size) {
            1 -> method.returns(sequenceFlowType).addStatement($$"return $L", constructions.single())

            else -> method.returns(ParameterizedTypeName.get(ClassName.get(List::class.java), sequenceFlowType))
                .addStatement($$"return $T.of($L)", List::class.java, CodeBlock.join(constructions, ", "))
        }.build()
    }

    private fun sequenceFlowConstruction(flow: SequenceFlowEdge, target: JavaFlowNodeType): CodeBlock = when {
        flow.hasOnlyDefaults() -> CodeBlock.of($$"new $T<>(new $T($S), $L)", JavaRuntimeTypes.SEQUENCE_FLOW, JavaRuntimeTypes.ELEMENT_ID, flow.id, target.instance())

        else -> CodeBlock.of(
            $$"new $T<>(new $T($S), $S, $S, $L, $L)",
            JavaRuntimeTypes.SEQUENCE_FLOW,
            JavaRuntimeTypes.ELEMENT_ID,
            flow.id,
            flow.name,
            flow.conditionExpression,
            flow.isDefault,
            target.instance(),
        )
    }

    private companion object {
        private const val ELEMENT_ID = "ELEMENT_ID"
        private const val NEXT_HOLDER = "Next"
        private const val OUTGOING_FLOWS_HOLDER = "OutgoingFlows"
        private const val START_HOLDER = "Start"
    }
}
