package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptCodeFormatTest {

    private val underTest = TypeScriptCodeFormat

    @Test
    fun `stringLiteral escapes quotes, backslashes and control characters`() {
        assertThat(underTest.stringLiteral(value = "say \"hi\"\r\n\tC:\\temp")).isEqualTo(""""say \"hi\"\r\n\tC:\\temp"""")
    }

    @Test
    fun `stringLiteral keeps an engine expression verbatim, as double quotes do not interpolate`() {
        assertThat(underTest.stringLiteral(value = $$"${applicationId}")).isEqualTo($$"\"${applicationId}\"")
    }

    @Test
    fun `nullableStringLiteral writes a missing value as null`() {
        assertThat(underTest.nullableStringLiteral(value = null)).isEqualTo("null")
    }

    @Test
    fun `propertyKey keeps an identifier and quotes anything else`() {
        assertThat(underTest.propertyKey(name = "ServiceTaskSendContract")).isEqualTo("ServiceTaskSendContract")
        assertThat(underTest.propertyKey(name = "Task.approve")).isEqualTo("\"Task.approve\"")
    }

    @Test
    fun `nameAndCode writes an error or escalation as an object literal`() {
        assertThat(underTest.nameAndCode(name = "miravelo.applicationInvalid", code = "applicationInvalid"))
            .isEqualTo("{ name: \"miravelo.applicationInvalid\", code: \"applicationInvalid\" }")
    }
}
