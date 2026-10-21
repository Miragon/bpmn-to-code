package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinSyntaxTest {

    @Test
    fun `stringLiteral escapes dollar signs so engine expressions are no string templates`() {
        assertThat(KotlinSyntax.stringLiteral("\${applicationId}")).isEqualTo("\"\\\${applicationId}\"")
    }

    @Test
    fun `stringLiteral escapes quotes, backslashes and line breaks`() {
        assertThat(KotlinSyntax.stringLiteral("a\"b\\c\nd")).isEqualTo("\"a\\\"b\\\\c\\nd\"")
    }

    @Test
    fun `identifier keeps a plain name`() {
        assertThat(KotlinSyntax.identifier("serviceTaskSendContract")).isEqualTo("serviceTaskSendContract")
    }

    @Test
    fun `identifier puts a keyword in backticks`() {
        assertThat(KotlinSyntax.identifier("in")).isEqualTo("`in`")
    }
}
