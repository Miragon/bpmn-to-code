package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.Placement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinCodeFormatTest {

    @Test
    fun `stringLiteral writes a plain value as a quoted string`() {
        assertThat(KotlinCodeFormat.stringLiteral("sendContract").toString()).isEqualTo("\"sendContract\"")
    }

    @Test
    fun `stringLiteral writes an engine expression as a multi-dollar raw string`() {
        assertThat(KotlinCodeFormat.stringLiteral($$"${applicationId}").toString()).isEqualTo($$"$$\"\"\"${applicationId}\"\"\"")
    }

    @Test
    fun `nullableStringLiteral writes a missing value as null`() {
        assertThat(KotlinCodeFormat.nullableStringLiteral(null).toString()).isEqualTo("null")
    }

    @Test
    fun `namedCall puts every argument on its own line and leaves out null arguments`() {
        // when: a call with one present and one missing argument is formatted
        val actual = KotlinCodeFormat.namedCall(wrapper, "name" to CodeBlock.of("%S", "a"), "code" to null)

        // then: only the present argument is written, with a trailing comma
        assertThat(actual.toString()).isEqualTo("io.example.Wrapper(\n  name = \"a\",\n)")
    }

    @Test
    fun `namedCall placed as initializer stays aligned with its property`() {
        // when: the call initializes a property
        val call = KotlinCodeFormat.namedCall(wrapper, "name" to CodeBlock.of("%S", "a"), placement = Placement.INITIALIZER)
        val actual = PropertySpec.builder("x", wrapper).initializer(call).build()

        // then: the arguments are indented one level and the closing parenthesis aligns with `val`
        assertThat(actual.toString()).isEqualTo("val x: io.example.Wrapper = io.example.Wrapper(\n  name = \"a\",\n)\n")
    }

    @Test
    fun `namedArguments lays out a supertype's constructor arguments like namedCall`() {
        // when: the arguments are handed to a supertype's constructor call
        val arguments = KotlinCodeFormat.namedArguments("name" to CodeBlock.of("%S", "a"), "code" to null)
        val actual = TypeSpec.objectBuilder("Node").superclass(wrapper).addSuperclassConstructorParameter(arguments).build()

        // then: only the present argument is written, on its own line with a trailing comma
        assertThat(actual.toString()).isEqualTo("public object Node : io.example.Wrapper(\n  name = \"a\",\n)\n")
    }

    @Test
    fun `listOfNames lists every name on its own line`() {
        val actual = PropertySpec.builder("all", wrapper).initializer(KotlinCodeFormat.listOfNames(listOf("A", "B"))).build()

        assertThat(actual.toString()).isEqualTo("val all: io.example.Wrapper = listOf(\n  A,\n  B,\n)\n")
    }

    @Test
    fun `listOfNames writes an empty list as emptyList`() {
        assertThat(KotlinCodeFormat.listOfNames(emptyList()).toString()).isEqualTo("emptyList()")
    }

    @Test
    fun `withoutPublicModifiers removes line-leading modifiers but keeps string content`() {
        val actual = KotlinCodeFormat.withoutPublicModifiers("public object A {\n  public val b = \"public c\"\n}")

        assertThat(actual).isEqualTo("object A {\n  val b = \"public c\"\n}")
    }

    private val wrapper = ClassName("io.example", "Wrapper")
}
