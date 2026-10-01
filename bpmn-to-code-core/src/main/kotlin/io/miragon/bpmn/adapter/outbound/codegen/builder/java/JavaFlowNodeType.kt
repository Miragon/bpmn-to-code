package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.FlowEdge
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * The generated Java type of a flow node: a singleton behind `INSTANCE`, so every reference to the node yields
 * the same object — like a Kotlin `object` or the C# `Instance`. Every node is a class named after the node
 * ([typeName]). A node that branches additionally has a `Next.<Name>` marker that each of its successors implements
 * ([successorMarker]). The markers share one `Next` holder instead of being nested in their nodes: two branching
 * nodes in a loop would otherwise inherit cyclically.
 */
internal class JavaFlowNodeType(objectName: String) {

    constructor(edge: FlowEdge) : this(edge.objectName)

    val typeName: ClassName = ClassName.get("", objectName)

    val successorMarker: ClassName = ClassName.get("", NEXT, objectName)

    fun instanceField(): FieldSpec = FieldSpec.builder(typeName, INSTANCE, PUBLIC, STATIC, FINAL)
        .initializer($$"new $T()", typeName).build()

    fun instance(): CodeBlock = CodeBlock.of($$"$T.$N", typeName, INSTANCE)

    companion object {
        const val NEXT = "Next"
        private const val INSTANCE = "INSTANCE"
    }
}
