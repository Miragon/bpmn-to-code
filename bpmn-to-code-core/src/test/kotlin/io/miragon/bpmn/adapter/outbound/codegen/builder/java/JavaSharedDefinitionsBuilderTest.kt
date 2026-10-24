package io.miragon.bpmn.adapter.outbound.codegen.builder.java

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaSharedDefinitionsBuilderTest {

    private val underTest = CodeGenerationAdapter()

    @Test
    fun `generateSharedCode generates one file per kind of shared definition`() {
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val definitions = miraVeloSharedDefinitions()

        // when: we build the shared definition files
        val result = underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.JAVA, packagePath = "de.emaarco.example")

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/java")
    }

    @Test
    fun `generateSharedCode skips kinds without definitions`() {
        // given: no shared definitions at all
        val definitions = SharedDefinitions()

        // when / then: no file is generated
        assertThat(underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.JAVA, packagePath = "de.emaarco.example")).isEmpty()
    }
}
