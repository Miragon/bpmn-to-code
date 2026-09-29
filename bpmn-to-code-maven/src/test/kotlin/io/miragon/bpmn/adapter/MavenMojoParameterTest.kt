package io.miragon.bpmn.adapter

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MavenMojoParameterTest {

    @ParameterizedTest
    @MethodSource("mojos")
    fun `fails with the valid values when processEngine is missing`(mojo: AbstractMojo) {
        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoFailureException::class.java)
            .hasMessage("processEngine is required (valid values: ZEEBE, CAMUNDA_7, OPERATON)")
    }

    @ParameterizedTest
    @MethodSource("mojos")
    fun `fails with the valid values when processEngine is unknown`(mojo: AbstractMojo) {
        setField(obj = mojo, name = "processEngine", value = "CAMUNDA7")

        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoFailureException::class.java)
            .hasMessage("processEngine 'CAMUNDA7' is not supported (valid values: ZEEBE, CAMUNDA_7, OPERATON)")
    }

    @Test
    fun `fails with the valid values when outputLanguage is unknown`() {
        val mojo = BpmnModelMojo()
        setField(obj = mojo, name = "outputLanguage", value = "SCALA")
        setField(obj = mojo, name = "processEngine", value = "ZEEBE")

        assertThatThrownBy { mojo.execute() }
            .isInstanceOf(MojoFailureException::class.java)
            .hasMessage("outputLanguage 'SCALA' is not supported (valid values: KOTLIN, JAVA, CSHARP)")
    }

    companion object {

        @JvmStatic
        fun mojos(): List<Named<AbstractMojo>> = listOf(
            named("generate-bpmn-api", BpmnModelMojo().apply { setField(obj = this, name = "outputLanguage", value = "KOTLIN") }),
            named("generate-bpmn-json", BpmnJsonMojo()),
            named("validate-bpmn", BpmnValidateMojo()),
        )
    }
}
