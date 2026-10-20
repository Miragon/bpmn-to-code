package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testProcessModelApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CodeGenerationAdapterTest {

    private val kotlinProcessBuilder = mockk<ProcessApiBuilder>(relaxed = true)
    private val kotlinSharedBuilder = mockk<SharedDefinitionsBuilder>(relaxed = true)
    private val underTest = CodeGenerationAdapter(
        generators = mapOf(
            OutputLanguage.KOTLIN to LanguageGenerator(
                fileExtension = "kt",
                processApiBuilder = kotlinProcessBuilder,
                sharedDefinitionsBuilder = kotlinSharedBuilder,
            ),
        ),
    )

    @Test
    fun `generateCode renders the process api into a file named after it`() {
        // given: a Kotlin model API and a stubbed process builder
        val modelApi = testProcessModelApi()
        every { kotlinProcessBuilder.render(api = any()) } returns "content"

        // when: generating code for the model
        val result = underTest.generateCode(modelApi)

        // then: the builder renders the neutral process api, and the adapter names the file after it
        assertThat(result).containsExactly(
            GeneratedApiFile(
                fileName = "OrderProcessApi.kt",
                packagePath = "packagePath",
                content = "content",
                language = OutputLanguage.KOTLIN,
                processId = "order",
            ),
        )
        verify { kotlinProcessBuilder.render(api = ProcessApiGraph.of(modelApi)) }
        confirmVerified(kotlinProcessBuilder)
    }

    @Test
    fun `generateCode throws when output language is not supported`() {
        val modelApi = testProcessModelApi(language = OutputLanguage.JAVA)

        assertThatThrownBy { underTest.generateCode(modelApi) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generateSharedCode renders one file per kind of shared definition`() {
        // given: a message and an error, and a stubbed shared builder
        val api = sharedDefinitionsApi(
            definitions = SharedDefinitions(
                messages = listOf(RootElementDefinition.Message(id = "message_bikeReturned", name = "bikeReturned")),
                errors = listOf(RootElementDefinition.Error(id = "error_damaged", name = "bikeDamaged", code = "DAMAGED")),
            ),
        )
        every { kotlinSharedBuilder.render(group = any(), packagePath = "packagePath") } answers {
            "content of ${firstArg<SharedDefinitionsGraph.Group>().type.typeName}"
        }

        // when: generating the shared code
        val result = underTest.generateSharedCode(api)

        // then: each kind is rendered into a file named after its type
        assertThat(result).containsExactly(
            sharedFile(fileName = "Messages.kt", content = "content of Messages"),
            sharedFile(fileName = "Errors.kt", content = "content of Errors"),
        )
        verify { kotlinSharedBuilder.render(group = match { it.type == SharedDefinitionType.MESSAGES }, packagePath = "packagePath") }
        verify { kotlinSharedBuilder.render(group = match { it.type == SharedDefinitionType.ERRORS }, packagePath = "packagePath") }
        confirmVerified(kotlinSharedBuilder)
    }

    @Test
    fun `generateSharedCode renders nothing without shared definitions`() {
        val result = underTest.generateSharedCode(sharedDefinitionsApi(definitions = SharedDefinitions()))

        assertThat(result).isEmpty()
        confirmVerified(kotlinSharedBuilder)
    }

    @Test
    fun `generateSharedCode throws when output language is not supported`() {
        val api = sharedDefinitionsApi(definitions = SharedDefinitions(), language = OutputLanguage.JAVA)

        assertThatThrownBy { underTest.generateSharedCode(api) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sharedDefinitionsApi(
        definitions: SharedDefinitions,
        language: OutputLanguage = OutputLanguage.KOTLIN,
    ): SharedDefinitionsApi = SharedDefinitionsApi(
        definitions = definitions,
        outputLanguage = language,
        packagePath = "packagePath",
    )

    private fun sharedFile(fileName: String, content: String): GeneratedApiFile = GeneratedApiFile(
        fileName = fileName,
        packagePath = "packagePath",
        content = content,
        language = OutputLanguage.KOTLIN,
        processId = null,
    )
}
