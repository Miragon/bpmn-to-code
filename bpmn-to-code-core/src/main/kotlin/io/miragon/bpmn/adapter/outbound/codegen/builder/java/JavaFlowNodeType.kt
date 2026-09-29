package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * The generated Java class of a flow node: a singleton behind `INSTANCE`, so every reference to the node
 * yields the same object — like a Kotlin `object` or the C# `Instance`.
 */
internal class JavaFlowNodeType(objectName: String) {

    val className: ClassName = ClassName.get("", objectName)

    fun instanceField(): FieldSpec = FieldSpec.builder(className, INSTANCE, PUBLIC, STATIC, FINAL)
        .initializer("new \$T()", className).build()

    fun instance(): CodeBlock = CodeBlock.of("\$T.\$N", className, INSTANCE)

    private companion object {
        private const val INSTANCE = "INSTANCE"
    }
}
