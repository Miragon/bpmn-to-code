package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import freemarker.template.SimpleScalar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptSourceFileTest {

    private val underTest = TypeScriptSourceFile()

    @Test
    fun `runtime names resolve any name a template asks for and are never empty`() {
        // when: a template reads a runtime type and a runtime value
        val type = underTest.runtimeTypes["FlowNode"] as SimpleScalar
        val value = underTest.runtimeValues["sequenceFlows"] as SimpleScalar

        // then: both are qualified with the runtime import, and neither model reports itself as empty
        assertThat(type.asString).isEqualTo("Runtime.FlowNode")
        assertThat(value.asString).isEqualTo("Runtime.sequenceFlows")
        assertThat(underTest.runtimeTypes.isEmpty).isFalse()
        assertThat(underTest.runtimeValues.isEmpty).isFalse()
    }
}
