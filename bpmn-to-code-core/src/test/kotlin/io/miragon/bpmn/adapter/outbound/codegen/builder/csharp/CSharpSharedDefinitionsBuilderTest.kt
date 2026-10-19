package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.assertMatchesGoldenFiles
import io.miragon.bpmn.adapter.outbound.codegen.builder.miraVeloSharedDefinitionsApi
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
        // given: shared definitions of every kind, taken from the MiraVelo processes
        val api = miraVeloSharedDefinitionsApi(OutputLanguage.CSHARP)

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api)

        // then: every file belongs to no process and matches the golden output
        assertThat(result).allMatch { it.processId == null && it.packagePath == "de.emaarco.example" }
        assertMatchesGoldenFiles(result, "/api/shared-definitions/csharp")
    }

    @Test
    fun `renames a constant that would collide with its enclosing type`() {
        // given: a message named exactly like the shared class that will contain it
        val definitions = SharedDefinitions(messages = listOf(RootElementDefinition.Message(id = "Messages", name = "Messages")))
        val api = SharedDefinitionsApi(
            definitions = definitions,
            outputLanguage = OutputLanguage.CSHARP,
            packagePath = "de.emaarco.example",
        )

        // when: we build the shared definition files
        val result = underTest.buildApiFiles(api).single()

        // then: the constant is renamed, because C# rejects a member named like its enclosing type (CS0542)
        assertThat(result.content).contains("public static readonly global::Miragon.BpmnToCode.Runtime.MessageName Messages_ = new(\"Messages\");")
        assertThat(result.content).doesNotContain(" Messages =")
    }

    @Test
    fun `job types and variable names are constants, the other kinds the runtime types the nodes carry`() {
        val result = underTest.buildApiFiles(miraVeloSharedDefinitionsApi(OutputLanguage.CSHARP)).associate { it.fileName to it.content }

        assertThat(result.getValue("ServiceTasks.cs")).contains("public const string MiraveloOrderBike = \"miravelo.orderBike\";")
        assertThat(result.getValue("ProcessVariables.cs")).contains("public const string ApplicationId = \"applicationId\";")
        assertThat(result.getValue("Signals.cs")).contains("public static readonly global::Miragon.BpmnToCode.Runtime.SignalName ")
        assertThat(result.getValue("Errors.cs")).contains(
            "public static readonly global::Miragon.BpmnToCode.Runtime.BpmnErrorDefinition MiraveloApplicationInvalid = new(\"miravelo.applicationInvalid\", \"applicationInvalid\");",
        )
    }

    @Test
    fun `buildApiFiles skips kinds without definitions`() {
        // given: no shared definitions at all
        val api = SharedDefinitionsApi(
            definitions = SharedDefinitions(),
            outputLanguage = OutputLanguage.CSHARP,
            packagePath = "de.emaarco.example",
        )

        // when / then: no file is generated
        assertThat(underTest.buildApiFiles(api)).isEmpty()
    }
}
