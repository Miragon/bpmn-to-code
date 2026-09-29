package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin

import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinSharedDefinitionsBuilderTest {

    private val underTest = KotlinSharedDefinitionsBuilder()

    @Test
    fun `buildApiFiles generates one file per kind of shared definition`() {
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.KOTLIN)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/kotlin")
    }

    @Test
    fun `strips the public modifier without touching string literals`() {
        // given: a message whose name contains the word "public"
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "msg", name = "Message_public reply")))
        val api = SharedDefinitionsApi(
            definitions = definitions,
            outputLanguage = OutputLanguage.KOTLIN,
            packagePath = "de.emaarco.example",
        )

        // when
        val result = underTest.buildApiFiles(api).single()

        // then
        assertThat(result.content).contains("MessageName(\"Message_public reply\")")
        assertThat(result.content).doesNotContainPattern("(?m)^\\s*public ")
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(
            definitions = SharedDefinitions(),
            outputLanguage = OutputLanguage.KOTLIN,
            packagePath = "de.emaarco.example",
        )

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
