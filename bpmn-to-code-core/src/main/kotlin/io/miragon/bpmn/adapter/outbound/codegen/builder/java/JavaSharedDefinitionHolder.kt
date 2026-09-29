package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.shared.VariableMapping
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

/**
 * The generated Java class of one kind of shared definition: a field per definition plus an `all()` accessor.
 */
internal class JavaSharedDefinitionHolder(type: SharedDefinitionType, javadoc: String) {

    private val holder = JavaConstantHolder(type.typeName).builder().addJavadoc(javadoc)

    fun withConstants(definitions: List<VariableMapping<String>>): TypeSpec {
        val stringClass = ClassName.get(String::class.java)
        definitions.forEach { holder.addField(field(it, stringClass).initializer("\$S", it.getValue()).build()) }
        return holder.addMethod(all(definitions, stringClass)).build()
    }

    fun withNames(definitions: List<VariableMapping<String>>, wrapperClass: ClassName): TypeSpec {
        definitions.forEach {
            holder.addField(field(it, wrapperClass).initializer("new \$T(\$S)", wrapperClass, it.getValue()).build())
        }
        return holder.addMethod(all(definitions, wrapperClass)).build()
    }

    fun withNamesAndCodes(definitions: List<VariableMapping<Pair<String, String>>>, wrapperClass: ClassName): TypeSpec {
        definitions.forEach {
            val (name, code) = it.getValue()
            holder.addField(field(it, wrapperClass).initializer("new \$T(\$S, \$S)", wrapperClass, name, code).build())
        }
        return holder.addMethod(all(definitions, wrapperClass)).build()
    }

    private fun field(definition: VariableMapping<*>, type: ClassName): FieldSpec.Builder = FieldSpec.builder(type, definition.getName(), PUBLIC, STATIC, FINAL)

    private fun all(definitions: List<VariableMapping<*>>, elementType: ClassName): MethodSpec {
        val fields = definitions.map { CodeBlock.of("\$N", it.getName()) }
        return MethodSpec.methodBuilder("all").addModifiers(PUBLIC, STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), elementType))
            .addStatement("return \$T.of(\n\$L)", List::class.java, CodeBlock.join(fields, ",\n")).build()
    }
}
