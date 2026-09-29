package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeSpec
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * The raw names behind a holder's typed wrappers as `static final String`s, so they fit where Java demands
 * compile-time constants — annotation arguments and `switch` labels.
 */
internal class JavaNamesHolder(private val constants: List<Pair<String, String>>) {

    fun build(): TypeSpec {
        val holder = TypeSpec.classBuilder(NAME).addModifiers(PUBLIC, STATIC, FINAL)
        constants.forEach { (name, value) ->
            holder.addField(FieldSpec.builder(String::class.java, name, PUBLIC, STATIC, FINAL).initializer("\$S", value).build())
        }
        return holder.build()
    }

    companion object {
        const val NAME = "Names"
    }
}
