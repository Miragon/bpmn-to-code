package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaSharedDefinitionsBuilderTest {

    private val underTest = JavaSharedDefinitionsBuilder()

    @Test
    fun `buildApiFiles generates one file per kind of shared definition`() {
        // given: the shared definitions of the bike-leasing and cancel-bike-order processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.JAVA)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/java")
    }

    @Test
    fun `signals are built from their raw names`() {
        // given: a single signal
        val definitions = SharedDefinitions(signals = listOf(RootElementDefinition.Signal(id = "sig", name = "miravelo.recallAnnounced")))
        val api = SharedDefinitionsApi(
            definitions = definitions,
            outputLanguage = OutputLanguage.JAVA,
            packagePath = "de.emaarco.example",
        )

        // when
        val result = underTest.buildApiFiles(api).single()

        // then: the typed wrapper refers to its compile-time constant
        assertThat(result.content).contains("SignalName MIRAVELO_RECALL_ANNOUNCED = new SignalName(Names.MIRAVELO_RECALL_ANNOUNCED);")
        assertThat(result.content).contains("public static final String MIRAVELO_RECALL_ANNOUNCED = \"miravelo.recallAnnounced\";")
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(
            definitions = SharedDefinitions(),
            outputLanguage = OutputLanguage.JAVA,
            packagePath = "de.emaarco.example",
        )

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
