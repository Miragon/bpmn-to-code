package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.domain.ProcessApiNamingException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `memberOf rejects a member named like its enclosing type`() {
        assertThatThrownBy { underTest.memberOf(name = "Messages", enclosingType = "Messages") }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("'Messages'")
    }

    @Test
    fun `memberOf keeps any other member name`() {
        assertThat(underTest.memberOf(name = "MiraveloOrderBike", enclosingType = "Messages")).isEqualTo("MiraveloOrderBike")
    }

    @Test
    fun `namespaceOf writes every segment of a package path in PascalCase`() {
        assertThat(underTest.namespaceOf(packagePath = "io.miravelo.process")).isEqualTo("Io.Miravelo.Process")
        assertThat(underTest.namespaceOf(packagePath = "MiraVelo.Process")).isEqualTo("MiraVelo.Process")
    }
}
