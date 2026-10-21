package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptSharedDefinitionsBuilderTest {

    private val underTest = CodeGenerationAdapter()

    @Test
    fun `generateSharedCode generates one file per kind of shared definition`() {
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.TYPESCRIPT)

        // when: we build the shared definition files
        val result = underTest.generateSharedCode(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/typescript")
    }

    @Test
    fun `generateSharedCode skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(
            definitions = SharedDefinitions(),
            outputLanguage = OutputLanguage.TYPESCRIPT,
            packagePath = "de.emaarco.example",
        )

        // when: we build the shared definition files
        val result = underTest.generateSharedCode(api)

        // then: no file is written
        assertThat(result).isEmpty()
    }
}
