package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaSharedDefinitionsBuilderTest {

    private val underTest = JavaSharedDefinitionsBuilder()

    @Test
    fun `buildApiFiles generates one file per kind of shared definition`() {
        // given: the shared definitions of the newsletter processes
        val api = newsletterSharedDefinitionsApi(OutputLanguage.JAVA)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertThat(result.asGoldenText()).isEqualTo(readGolden("/api/SharedDefinitionsJava.txt", result.asGoldenText()))
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(SharedDefinitions(), OutputLanguage.JAVA, "de.emaarco.example")

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
