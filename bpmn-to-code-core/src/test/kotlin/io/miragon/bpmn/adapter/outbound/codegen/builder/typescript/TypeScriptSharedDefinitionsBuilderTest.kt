package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptSharedDefinitionsBuilderTest {

    private val underTest = TypeScriptSharedDefinitionsBuilder()

    @Test
    fun `buildApiFiles generates one file per kind of shared definition`() {
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.TYPESCRIPT)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/typescript")
    }

    @Test
    fun `buildApiFiles skips kinds without definitions and writes no runtime types of its own`() {
        val api = SharedDefinitionsApi(definitions = SharedDefinitions(), outputLanguage = OutputLanguage.TYPESCRIPT, packagePath = "")

        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }

    @Test
    fun `definitions with a code import their runtime type from the runtime package`() {
        val result = underTest.buildApiFiles(miraVeloSharedDefinitionsApi(OutputLanguage.TYPESCRIPT)).single { it.fileName == "Errors.ts" }

        assertThat(result.content).contains("import type * as Runtime from \"@miragon/bpmn-to-code-runtime\";")
    }
}
