package io.miragon.bpmn.adapter.outbound.codegen.builder.java.freemarker

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JavaSyntaxTest {

    @Test
    fun `stringLiteral keeps dollar signs, as Java has no string templates`() {
        assertThat(JavaSyntax.stringLiteral("\${applicationId}")).isEqualTo("\"\${applicationId}\"")
    }

    @Test
    fun `stringLiteral escapes quotes, backslashes and control characters`() {
        assertThat(JavaSyntax.stringLiteral("a\"b\\c\nd\te\rf\bg\u000ch\u0001")).isEqualTo("\"a\\\"b\\\\c\\nd\\te\\rf\\bg\\fh\\u0001\"")
    }

    @Test
    fun `nullableStringLiteral writes null for a missing value`() {
        assertThat(JavaSyntax.nullableStringLiteral(null)).isEqualTo("null")
    }

    @Test
    fun `identifier keeps a valid name`() {
        assertThat(JavaSyntax.identifier("serviceTaskSendContract")).isEqualTo("serviceTaskSendContract")
    }

    @Test
    fun `identifier rejects a keyword`() {
        assertThatThrownBy { JavaSyntax.identifier("class") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
