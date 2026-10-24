package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
import io.miragon.bpmn.domain.ProcessApiNamingException
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
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
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.CSHARP)

        // when: we build the shared definition files
        val result = underTest.generateSharedCode(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/csharp")
    }

    @Test
    fun `fails on a constant that would be named like its enclosing type`() {
        // given: a message named exactly like the shared class that will contain it
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "Messages", name = "Messages")))
        val api = SharedDefinitionsApi(
            definitions = definitions,
            outputLanguage = OutputLanguage.CSHARP,
            packagePath = "de.emaarco.example",
        )

        // when / then: generation fails, because C# rejects a member named like its enclosing type (CS0542)
        assertThatThrownBy { underTest.generateSharedCode(api) }
            .isInstanceOf(ProcessApiNamingException::class.java)
            .hasMessageContaining("'Messages'")
    }

    @Test
    fun `generateSharedCode skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(
            definitions = SharedDefinitions(),
            outputLanguage = OutputLanguage.CSHARP,
            packagePath = "de.emaarco.example",
        )

        // when / then: no file is generated
        assertThat(underTest.generateSharedCode(api)).isEmpty()
    }
}
