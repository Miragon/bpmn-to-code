package io.miragon.bpmn.adapter.outbound.codegen.builder

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal const val KOTLIN_NAMES_HOLDER = "Names"

/**
 * The raw names behind a holder's typed wrappers as `const val String`s, so they fit where Kotlin demands
 * compile-time constants — annotation arguments and `when` branches.
 */
internal fun kotlinNamesHolder(constants: List<Pair<String, String>>): TypeSpec {
    val holder = TypeSpec.objectBuilder(KOTLIN_NAMES_HOLDER)
    constants.forEach { (name, value) ->
        holder.addProperty(PropertySpec.builder(name, String::class).addModifiers(KModifier.CONST).initializer("%L", kotlinStringLiteral(value)).build())
    }
    return holder.build()
}
