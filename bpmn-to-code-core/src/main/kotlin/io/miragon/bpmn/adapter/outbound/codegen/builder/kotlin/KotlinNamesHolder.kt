package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * The raw names behind a holder's typed wrappers as `const val String`s, so they fit where Kotlin demands
 * compile-time constants — annotation arguments and `when` branches.
 */
internal class KotlinNamesHolder(private val constants: List<Pair<String, String>>) {

    fun build(): TypeSpec {
        val holder = TypeSpec.objectBuilder(NAME)
        constants.forEach { (name, value) ->
            holder.addProperty(PropertySpec.builder(name, String::class).addModifiers(KModifier.CONST).initializer("%L", kotlinStringLiteral(value)).build())
        }
        return holder.build()
    }

    companion object {
        const val NAME = "Names"
    }
}
