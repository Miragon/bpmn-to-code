package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TypeScriptSyntaxTest {

    private val underTest = TypeScriptSyntax

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
    fun `identifier keeps a valid name`() {
        assertThat(underTest.identifier(name = "ServiceTaskSendContract")).isEqualTo("ServiceTaskSendContract")
    }

    @Test
    fun `identifier keeps a name with non-ascii letters`() {
        assertThat(underTest.identifier(name = "ServiceTaskPrüfen")).isEqualTo("ServiceTaskPrüfen")
    }

    @Test
    fun `identifier rejects a name that would not compile`() {
        assertThatThrownBy { underTest.identifier(name = "Task.Approve") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { underTest.identifier(name = "1") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
