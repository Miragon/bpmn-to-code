package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CSharpSharedDefinitionsBuilderTest {

    private val underTest = CSharpSharedDefinitionsBuilder()

    @Test
    fun `buildApiFiles generates one file per kind of shared definition`() {
        // given: the shared definitions of the newsletter processes
        val api = newsletterSharedDefinitionsApi(OutputLanguage.CSHARP)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertThat(result.asGoldenText()).isEqualTo(readGolden("/api/SharedDefinitionsCsharp.txt", result.asGoldenText()))
    }

    @Test
    fun `renames a constant that would collide with its enclosing type`() {
        // given: a message named exactly like the shared class that will contain it
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "Messages", name = "Messages")))
        val api = SharedDefinitionsApi(definitions, OutputLanguage.CSHARP, "de.emaarco.example")

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api).single()

        // then: the constant is renamed, because C# rejects a member named like its enclosing type (CS0542)
        assertThat(result.content).contains("public const string Messages_ = \"Messages\";")
        assertThat(result.content).doesNotContain("public const string Messages =")
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(SharedDefinitions(), OutputLanguage.CSHARP, "de.emaarco.example")

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
