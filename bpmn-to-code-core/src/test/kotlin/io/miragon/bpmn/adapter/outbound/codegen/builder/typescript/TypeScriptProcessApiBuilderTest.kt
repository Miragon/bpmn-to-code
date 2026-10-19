package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.assertMatchesGolden
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testCancelBikeOrderModel
import io.miragon.bpmn.domain.testProcessModelApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeScriptProcessApiBuilderTest {

    private val underTest = TypeScriptProcessApiBuilder()

    @Test
    fun `buildApiFile generates correct process API file`() {
        // given: the bike-leasing model, which covers every implementation kind of Camunda 7
        val modelApi = testProcessModelApi(packagePath = "de.emaarco.example", language = OutputLanguage.TYPESCRIPT, model = testBikeLeasingModel())

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: one module, placed in the directory of the package path, exporting the API
        assertThat(result.fileName).isEqualTo("BikeLeasingProcessApi.ts")
        assertThat(result.packagePath).isEqualTo("de.emaarco.example")
        assertThat(result.content).contains("export const BikeLeasingProcessApi = {")
        assertMatchesGolden(result.content, "/api/BikeLeasingProcessApiTypeScript.txt")
    }

    @Test
    fun `buildApiFile leads the API name with the variant name of the model`() {
        // given: a model that declares a variant name
        val modelApi = testProcessModelApi(
            packagePath = "de.emaarco.example",
            language = OutputLanguage.TYPESCRIPT,
            model = testCancelBikeOrderModel(variantName = "retail"),
        )

        // when: we build the process API file
        val result = underTest.buildApiFile(modelApi)

        // then: the variant name leads the API name, while the process id stays the one of the model
        assertThat(result.fileName).isEqualTo("RetailCancelBikeOrderProcessApi.ts")
        assertMatchesGolden(result.content, "/api/PrefixedCancelBikeOrderProcessApiTypeScript.txt")
    }

    @Test
    fun `imports only the shared definitions the process refers to`() {
        val result = underTest.buildApiFile(testProcessModelApi(language = OutputLanguage.TYPESCRIPT, model = testCancelBikeOrderModel()))

        assertThat(result.content).contains("import { ServiceTasks } from \"./ServiceTasks.js\";")
        assertThat(result.content).doesNotContain("import { Signals }", "import { Escalations }")
    }
}
