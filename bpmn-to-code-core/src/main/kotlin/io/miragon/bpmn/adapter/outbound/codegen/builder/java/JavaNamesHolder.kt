package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeSpec
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal const val JAVA_NAMES_HOLDER = "Names"

/**
 * The raw names behind a holder's typed wrappers as `static final String`s, so they fit where Java demands
 * compile-time constants — annotation arguments and `switch` labels.
 */
internal fun javaNamesHolder(constants: List<Pair<String, String>>): TypeSpec {
    val holder = TypeSpec.classBuilder(JAVA_NAMES_HOLDER).addModifiers(PUBLIC, STATIC, FINAL)
    constants.forEach { (name, value) ->
        holder.addField(FieldSpec.builder(String::class.java, name, PUBLIC, STATIC, FINAL).initializer("\$S", value).build())
    }
    return holder.build()
}
