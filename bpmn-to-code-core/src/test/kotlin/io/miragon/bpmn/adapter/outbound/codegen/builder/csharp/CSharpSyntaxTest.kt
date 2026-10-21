package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CSharpSyntaxTest {

    private val underTest = CSharpSyntax

    @Test
    fun `stringLiteral escapes quotes, backslashes and control characters`() {
        assertThat(underTest.stringLiteral(value = "say \"hi\"\r\n\tC:\\temp")).isEqualTo(""""say \"hi\"\r\n\tC:\\temp"""")
    }

    @Test
    fun `stringLiteral writes any other control character as a unicode escape`() {
        assertThat(underTest.stringLiteral(value = "a\u0001b")).isEqualTo("\"a\\u0001b\"")
    }

    @Test
    fun `stringLiteral keeps an engine expression verbatim`() {
        assertThat(underTest.stringLiteral(value = $$"${applicationId}")).isEqualTo($$"\"${applicationId}\"")
    }

    @Test
    fun `nullableStringLiteral writes a missing value as null`() {
        assertThat(underTest.nullableStringLiteral(value = null)).isEqualTo("null")
    }

    @Test
    fun `pascalCase joins the words of a BPMN name`() {
        assertThat(underTest.pascalCase(name = "miravelo.orderBike")).isEqualTo("MiraveloOrderBike")
    }

    @Test
    fun `pascalCase strips expression syntax`() {
        assertThat(underTest.pascalCase(name = $$"${sendContractDelegate}")).isEqualTo("SendContractDelegate")
    }

    @Test
    fun `pascalCase guards a leading digit`() {
        assertThat(underTest.pascalCase(name = "3rdReminder")).isEqualTo("_3RdReminder")
    }

    @Test
    fun `disambiguated renames a member named like its enclosing type`() {
        assertThat(underTest.disambiguated(name = "Messages", enclosingType = "Messages")).isEqualTo("Messages_")
    }

    @Test
    fun `disambiguated keeps any other member name`() {
        assertThat(underTest.disambiguated(name = "MiraveloOrderBike", enclosingType = "Messages")).isEqualTo("MiraveloOrderBike")
    }

    @Test
    fun `namespaceOf writes every segment of a package path in PascalCase`() {
        assertThat(underTest.namespaceOf(packagePath = "io.miravelo.process")).isEqualTo("Io.Miravelo.Process")
        assertThat(underTest.namespaceOf(packagePath = "MiraVelo.Process")).isEqualTo("MiraVelo.Process")
    }
}
