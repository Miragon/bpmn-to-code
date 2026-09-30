package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaConstantHolder
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * Writes the Java class of one kind of shared definition: a field per definition plus an `all()` accessor.
 */
internal abstract class JavaSharedDefinitionWriter<T : Any> {

    protected abstract val type: SharedDefinitionType
    protected abstract val javadoc: String
    protected abstract val elementType: ClassName

    protected abstract fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<T>>

    protected abstract fun initializer(value: T): CodeBlock

    fun shouldWrite(definitions: SharedDefinitions): Boolean = definitionsOf(definitions).isNotEmpty()

    fun write(definitions: SharedDefinitions): TypeSpec {
        val ofKind = definitionsOf(definitions)
        return JavaConstantHolder(type.typeName).builder().addJavadoc(javadoc)
            .addFields(ofKind.map { field(name = it.getName(), initializer = initializer(it.getValue())) })
            .addMethod(allAccessor(ofKind))
            .build()
    }

    /**
     * A node's value of this kind: its shared constant, or the value itself when no shared constant holds it.
     */
    fun nodeValue(shared: SharedValue<T>): CodeBlock {
        val constant = shared.constant
        return if (constant != null) reference(constant) else initializer(shared.value)
    }

    private fun reference(constant: SharedConstant): CodeBlock = CodeBlock.of($$"$T.$N", ClassName.get("", type.typeName), constant.name)

    private fun field(name: String, initializer: CodeBlock): FieldSpec = FieldSpec.builder(elementType, name, PUBLIC, STATIC, FINAL).initializer(initializer).build()

    private fun allAccessor(definitions: List<VariableMapping<T>>): MethodSpec {
        val fields = definitions.map { CodeBlock.of($$"$N", it.getName()) }
        return MethodSpec.methodBuilder("all").addModifiers(PUBLIC, STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), elementType))
            .addStatement($$"return $T.of(\n$L)", List::class.java, CodeBlock.join(fields, ",\n")).build()
    }
}
