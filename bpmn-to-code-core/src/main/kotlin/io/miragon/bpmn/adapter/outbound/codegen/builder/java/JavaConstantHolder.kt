package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC

/**
 * A generated Java class that only holds constants, accessors and nested types — `final` and not instantiable.
 */
internal class JavaConstantHolder(private val name: String) {

    fun builder(vararg modifiers: Modifier): TypeSpec.Builder = TypeSpec.classBuilder(name)
        .addModifiers(PUBLIC, *modifiers, FINAL)
        .addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).build())
}
