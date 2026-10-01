package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowGraphNode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SequenceFlowEdge
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Emits the typed navigation graph of a Java process API `FlowNodes` class: one nested node class per flow node,
 * carrying its metadata via `AbstractFlowNode`, its own facets (see [JavaFacetWriter]) and its successors in
 * `getOutgoing()`: the `SequenceFlows` to an element, or an attached boundary event. The edge lives in the type
 * hierarchy: a node leading to one element is a `LeadsTo` that element, and a node leading to several nests a
 * `Next` marker that every one of its successors implements and is a `LeadsTo` of that (see [JavaFlowNodeType]). All nodes are direct children of `FlowNodes`, whatever their subprocess depth; a subprocess
 * class additionally is a `FlowScope` whose `getStartEvents()` yields the interior's start elements, and a boundary event
 * is a `BoundaryEvent` of its host. Every node is a singleton (see [JavaFlowNodeType]), and `FlowNodes` exposes a
 * static accessor method per node and `all()` listing every node.
 */
internal class JavaFlowWriter {

    private val facetWriter = JavaFacetWriter()

    fun write(builder: TypeSpec.Builder, graph: FlowGraph) {
        graph.nodes.forEach { node -> builder.addMethod(nodeAccessor(node.propertyName, JavaFlowNodeType(node.objectName), PUBLIC, STATIC)) }
        builder.addMethod(allNodes(graph))
        graph.nodes.forEach { node -> builder.addType(buildNode(node)) }
        val branchingNodes = graph.nodes.filter { it.branches }
        if (branchingNodes.isNotEmpty()) {
            builder.addType(successorMarkers(branchingNodes))
        }
    }

    private fun allNodes(graph: FlowGraph): MethodSpec {
        val nodes = graph.nodes.map { JavaFlowNodeType(it.objectName).instance() }
        return MethodSpec.methodBuilder("all").addModifiers(PUBLIC, STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), JavaRuntimeTypes.FLOW_NODE))
            .addStatement($$"return $T.of(\n$L)", List::class.java, CodeBlock.join(nodes, ",\n")).build()
    }

    private fun buildNode(node: FlowGraphNode): TypeSpec {
        val nodeType = JavaFlowNodeType(node.objectName)
        val classBuilder = TypeSpec.classBuilder(node.objectName).addModifiers(PUBLIC, STATIC, FINAL)
        extendFlowNode(classBuilder, node)
        facetWriter.methods(node.facets).forEach { classBuilder.addMethod(it) }
        if (node.successors.isNotEmpty()) {
            addSuccessors(classBuilder, node)
        }
        if (node.interiorStarts.isNotEmpty()) {
            classBuilder.addMethod(accessorMethod("getStartEvents", START_HOLDER))
        }
        classBuilder.addField(FieldSpec.builder(String::class.java, ELEMENT_ID, PUBLIC, STATIC, FINAL).initializer($$"$S", node.id).build())
        classBuilder.addField(nodeType.instanceField())
        facetWriter.holders(node.facets).forEach { classBuilder.addType(it) }
        interiorStartsHolder(node)?.let { classBuilder.addType(it) }
        return classBuilder.build()
    }

    /** The `Next` holder: one marker per branching node, implemented by each of that node's successors. */
    private fun successorMarkers(branchingNodes: List<FlowGraphNode>): TypeSpec {
        val holder = JavaConstantHolder(JavaFlowNodeType.NEXT).builder(STATIC)
            .addJavadoc("One marker per element leading to several others, implemented by every element it leads to.\n")
        branchingNodes.forEach { node ->
            holder.addType(TypeSpec.interfaceBuilder(node.objectName).addModifiers(PUBLIC).addSuperinterface(JavaRuntimeTypes.FLOW_NODE).build())
        }
        return holder.build()
    }

    private fun extendFlowNode(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        classBuilder.superclass(JavaRuntimeTypes.ABSTRACT_FLOW_NODE)
        classBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).addStatement(superCall(node)).build())
        if (node.successors.isNotEmpty()) {
            classBuilder.addSuperinterface(ParameterizedTypeName.get(JavaRuntimeTypes.LEADS_TO, followingType(node)))
        }
        node.predecessors.filter { it.branches }.forEach { predecessor -> classBuilder.addSuperinterface(JavaFlowNodeType(predecessor).successorMarker) }
        val host = node.facets.attachedTo
        when {
            node.isBoundaryEvent && host != null ->
                classBuilder.addSuperinterface(ParameterizedTypeName.get(JavaRuntimeTypes.BOUNDARY_EVENT, JavaFlowNodeType(host).typeName))

            node.eventType != null -> classBuilder.addSuperinterface(JavaRuntimeTypes.EVENT)
        }
        if (node.interiorStarts.isNotEmpty()) {
            classBuilder.addSuperinterface(ownHolderInterface(interfaceType = JavaRuntimeTypes.FLOW_SCOPE, node = node, holderName = START_HOLDER))
        }
        classBuilder.addSuperinterfaces(facetWriter.superinterfaces(node.facets))
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

    private fun followingType(node: FlowGraphNode): ClassName = when {
        node.branches -> JavaFlowNodeType(node.objectName).successorMarker
        else -> JavaFlowNodeType(node.successors.single()).typeName
    }

    // A bare `Start` in the implements clause would bind to an enclosing class's `Start`; qualify with the node.
    private fun ownHolderInterface(interfaceType: ClassName, node: FlowGraphNode, holderName: String): ParameterizedTypeName {
        val ownHolder = ClassName.get("", node.objectName, holderName)
        return ParameterizedTypeName.get(interfaceType, ownHolder)
    }

    /**
     * A successor reached by sequence flows is the `SequenceFlows` carrying them; one reached without a flow is an
     * `AttachedBoundaryEvent`. A single flow is created via `SequenceFlows.single`, several flows to the same target
     * are listed.
     */
    private fun addSuccessors(classBuilder: TypeSpec.Builder, node: FlowGraphNode) {
        val successors = node.successors.map { successor ->
            val target = JavaFlowNodeType(successor)
            val flowsToTarget = node.outgoingFlows.find { it.target.objectName == successor.objectName }
            flowsToTarget?.let { sequenceFlowsConstruction(it.flows, target) } ?: CodeBlock.of($$"new $T<>($L)", JavaRuntimeTypes.ATTACHED_BOUNDARY_EVENT, target.instance())
        }
        val successorType = ParameterizedTypeName.get(JavaRuntimeTypes.SUCCESSOR, followingType(node))
        classBuilder.addMethod(
            MethodSpec.methodBuilder("getOutgoing").addAnnotation(Override::class.java).addModifiers(PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), successorType))
                .addStatement($$"return $T.of(\n$L)", List::class.java, CodeBlock.join(successors, ",\n")).build(),
        )
    }

    private fun interiorStartsHolder(node: FlowGraphNode): TypeSpec? {
        if (node.interiorStarts.isEmpty()) return null
        val holderBuilder = TypeSpec.classBuilder(START_HOLDER).addModifiers(PUBLIC, STATIC, FINAL)
        node.interiorStarts.forEach { start -> holderBuilder.addMethod(nodeAccessor(start.propertyName, JavaFlowNodeType(start), PUBLIC)) }
        return holderBuilder.build()
    }

    private fun accessorMethod(methodName: String, holderName: String): MethodSpec {
        val holderClass = ClassName.get("", holderName)
        return MethodSpec.methodBuilder(methodName).addAnnotation(Override::class.java).addModifiers(PUBLIC).returns(holderClass)
            .addStatement($$"return new $T()", holderClass).build()
    }

    private fun nodeAccessor(methodName: String, returnNode: JavaFlowNodeType, vararg modifiers: Modifier): MethodSpec = MethodSpec.methodBuilder(methodName).addModifiers(*modifiers).returns(returnNode.typeName)
        .addStatement($$"return $L", returnNode.instance()).build()

    private fun sequenceFlowsConstruction(flows: List<SequenceFlowEdge>, target: JavaFlowNodeType): CodeBlock {
        val singleFlow = flows.singleOrNull()
        if (singleFlow != null) {
            return CodeBlock.of($$"$T.single($L)", JavaRuntimeTypes.SEQUENCE_FLOWS, flowArguments(singleFlow, target))
        }
        val constructions = flows.map { CodeBlock.of($$"new $T<>($L)", JavaRuntimeTypes.SEQUENCE_FLOW, flowArguments(it, target)) }
        return CodeBlock.of($$"new $T<>($L, $T.of($L))", JavaRuntimeTypes.SEQUENCE_FLOWS, target.instance(), List::class.java, CodeBlock.join(constructions, ", "))
    }

    private fun flowArguments(flow: SequenceFlowEdge, target: JavaFlowNodeType): CodeBlock = when {
        flow.hasOnlyDefaults() -> CodeBlock.of($$"new $T($S), $L", JavaRuntimeTypes.ELEMENT_ID, flow.id, target.instance())

        else -> CodeBlock.of(
            $$"new $T($S), $S, $S, $L, $L",
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
        private const val START_HOLDER = "Start"
    }
}
