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
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinNamesHolder
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Writes the Kotlin object of one kind of shared definition: a property per definition plus `entries`.
 */
internal abstract class KotlinSharedDefinitionWriter<T : Any> {

    companion object {
        const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime"
    }

    protected abstract val type: SharedDefinitionType
    protected abstract val kdoc: String
    protected abstract val elementType: TypeName
    protected open val modifiers: List<KModifier> = emptyList()

    protected abstract fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<T>>

    protected abstract fun initializer(definition: VariableMapping<T>): CodeBlock

    protected open fun rawNames(definition: VariableMapping<T>): List<Pair<String, String>> = emptyList()

    fun shouldWrite(definitions: SharedDefinitions): Boolean = definitionsOf(definitions).isNotEmpty()

    fun write(definitions: SharedDefinitions): TypeSpec {
        val ofKind = definitionsOf(definitions)
        val holder = TypeSpec.objectBuilder(type.typeName).addKdoc(kdoc)
            .addProperties(ofKind.map { property(it) })
            .addProperty(entries(ofKind))
        val rawNames = ofKind.flatMap { rawNames(it) }
        if (rawNames.isNotEmpty()) holder.addType(KotlinNamesHolder(rawNames).build())
        return holder.build()
    }

    private fun property(definition: VariableMapping<T>): PropertySpec = PropertySpec.builder(definition.getName(), elementType)
        .addModifiers(modifiers).initializer(initializer(definition)).build()

    private fun entries(definitions: List<VariableMapping<T>>): PropertySpec = PropertySpec.builder("entries", LIST.parameterizedBy(elementType))
        .initializer(KotlinCodeFormat.listOfNames(definitions.map { it.getName() })).build()
}
