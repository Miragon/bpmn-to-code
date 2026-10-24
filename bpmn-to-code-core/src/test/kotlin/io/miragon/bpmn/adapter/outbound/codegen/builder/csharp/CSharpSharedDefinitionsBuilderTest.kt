package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitions
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CSharpSharedDefinitionsBuilderTest {

    private val underTest = CodeGenerationAdapter()

    @Test
    fun `generateSharedCode generates one file per kind of shared definition`() {
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val definitions = miraVeloSharedDefinitions()

        // when: we build the shared definition files
        val result = underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.CSHARP, packagePath = "de.emaarco.example")

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/csharp")
    }

    @Test
    fun `fails on a constant that would be named like its enclosing type`() {
        // given: a message named exactly like the shared class that will contain it
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "Messages", name = "Messages")))

        // when / then: generation fails, because C# rejects a member named like its enclosing type (CS0542)
        assertThatThrownBy { underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.CSHARP, packagePath = "de.emaarco.example") }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("'Messages'")
    }

    @Test
    fun `generateSharedCode skips kinds without definitions`() {
        // given: no shared definitions at all
        val definitions = SharedDefinitions()

        // when / then: no file is generated
        assertThat(underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.CSHARP, packagePath = "de.emaarco.example")).isEmpty()
    }
}
