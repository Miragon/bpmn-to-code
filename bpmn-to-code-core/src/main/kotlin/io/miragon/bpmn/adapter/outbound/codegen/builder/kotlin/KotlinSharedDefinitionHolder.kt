package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * The generated Kotlin object of one kind of shared definition: a property per definition plus `entries`.
 */
internal class KotlinSharedDefinitionHolder(type: SharedDefinitionType, kdoc: String) {

    private val holder = TypeSpec.objectBuilder(type.typeName).addKdoc(kdoc)

    fun withConstants(definitions: List<VariableMapping<String>>): TypeSpec {
        definitions.forEach {
            holder.addProperty(
                PropertySpec.builder(it.getName(), String::class).addModifiers(KModifier.CONST).initializer("%L", kotlinStringLiteral(it.getValue())).build(),
            )
        }
        return holder.addProperty(entries(definitions, String::class.asTypeName())).build()
    }

    fun withNames(definitions: List<VariableMapping<String>>, wrapperClass: ClassName): TypeSpec {
        definitions.forEach {
            holder.addProperty(PropertySpec.builder(it.getName(), wrapperClass).initializer("%T(%L)", wrapperClass, kotlinStringLiteral(it.getValue())).build())
        }
        return holder.addProperty(entries(definitions, wrapperClass)).build()
    }

    fun withNamesAndCodes(definitions: List<VariableMapping<Pair<String, String>>>, wrapperClass: ClassName): TypeSpec {
        definitions.forEach {
            val (name, code) = it.getValue()
            val initializer = kotlinNamedInitializer(wrapperClass, "name" to kotlinStringLiteral(name), "code" to kotlinStringLiteral(code))
            holder.addProperty(PropertySpec.builder(it.getName(), wrapperClass).initializer(initializer).build())
        }
        return holder.addProperty(entries(definitions, wrapperClass)).build()
    }

    private fun entries(definitions: List<VariableMapping<*>>, elementType: TypeName): PropertySpec = PropertySpec.builder("entries", LIST.parameterizedBy(elementType))
        .initializer(kotlinListOf(definitions.map { CodeBlock.of("%N", it.getName()) })).build()
}
