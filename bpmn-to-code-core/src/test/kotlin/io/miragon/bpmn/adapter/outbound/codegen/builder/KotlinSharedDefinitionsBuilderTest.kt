package io.miragon.bpmn.adapter.outbound.codegen.builder

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
        // given: the shared definitions of the bike-leasing and cancel-bike-order processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.KOTLIN)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertThat(result.asGoldenText()).isEqualTo(readGolden("/api/SharedDefinitionsKotlin.txt", result.asGoldenText()))
    }

    @Test
    fun `strips the public modifier without touching string literals`() {
        // given: a message whose name contains the word "public"
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "msg", name = "Message_public reply")))
        val api = SharedDefinitionsApi(definitions, OutputLanguage.KOTLIN, "de.emaarco.example")

        // when
        val result = underTest.buildApiFiles(api).single()

        // then
        assertThat(result.content).contains("const val MESSAGE_PUBLIC_REPLY: String = \"Message_public reply\"")
        assertThat(result.content).doesNotContainPattern("(?m)^\\s*public ")
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(SharedDefinitions(), OutputLanguage.KOTLIN, "de.emaarco.example")

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
