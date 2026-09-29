package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Writes the Kotlin object of one kind of shared definition: a property per definition plus `entries`.
 */
internal abstract class KotlinSharedDefinitionWriter<T : Any> {

    protected abstract val type: SharedDefinitionType
    protected abstract val kdoc: String
    protected abstract val elementType: TypeName
    protected open val modifiers: List<KModifier> = emptyList()

    protected abstract fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<T>>

    protected abstract fun initializer(value: T): CodeBlock

    fun shouldWrite(definitions: SharedDefinitions): Boolean = definitionsOf(definitions).isNotEmpty()

    fun write(definitions: SharedDefinitions): TypeSpec {
        val ofKind = definitionsOf(definitions)
        return TypeSpec.objectBuilder(type.typeName).addKdoc(kdoc)
            .addProperties(ofKind.map { property(name = it.getName(), initializer = initializer(it.getValue())) })
            .addProperty(entries(ofKind))
            .build()
    }

    /**
     * A node's property holding a value of this kind: its shared constant — the shared types live in the Process API's
     * package, so the plain name resolves without an import — or the value itself when no shared constant holds it.
     */
    fun nodeProperty(name: String, shared: SharedValue<T>): PropertySpec {
        val constant = shared.constant
        val initializer = if (constant != null) reference(constant) else initializer(shared.value)
        return property(name = name, initializer = initializer)
    }

    private fun reference(constant: SharedConstant): CodeBlock = CodeBlock.of("%L.%N", type.typeName, constant.name)

    private fun property(name: String, initializer: CodeBlock): PropertySpec = PropertySpec.builder(name, elementType)
        .addModifiers(modifiers).initializer(initializer).build()

    private fun entries(definitions: List<VariableMapping<T>>): PropertySpec = PropertySpec.builder("entries", LIST.parameterizedBy(elementType))
        .initializer(KotlinCodeFormat.listOfNames(definitions.map { it.getName() })).build()
}
