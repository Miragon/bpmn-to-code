package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.testBikeLeasingModel
import io.miragon.bpmn.domain.testProcessApiRequest
import io.miragon.bpmn.domain.testProcessModel
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
        // given: a Kotlin run of one model and a stubbed process builder
        val model = testProcessModel()
        val request = testProcessApiRequest(model = model)
        every { kotlinProcessBuilder.render(api = any()) } returns SourceCode("content")

        // when: generating code for the model
        val result = underTest.generateCode(request)

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
        verify { kotlinProcessBuilder.render(api = ProcessApiGraph.of(model, request)) }
        confirmVerified(kotlinProcessBuilder)
    }

    @Test
    fun `generate renders a process api per model and the shared definitions of the run`() {
        // given: a run of the bike-leasing model, whose job types become a shared definition file
        val request = ProcessApiRequest(
            models = listOf(testBikeLeasingModel()),
            outputLanguage = OutputLanguage.KOTLIN,
            packagePath = "io.miravelo.process",
            engine = ProcessEngine.ZEEBE,
        )
        every { kotlinProcessBuilder.render(api = any()) } returns SourceCode("api")
        every { kotlinSharedBuilder.render(group = any(), packagePath = any()) } returns SourceCode("shared")

        // when: generating the run
        val result = underTest.generate(request)

        // then: the Process API comes first, followed by one file per kind of shared definition
        assertThat(result.first().fileName).isEqualTo("BikeLeasingProcessApi.kt")
        assertThat(result.drop(1)).isNotEmpty().allMatch { it.processId == null && it.content == "shared" }
    }

    @Test
    fun `generateCode throws when output language is not supported`() {
        val request = testProcessApiRequest(language = OutputLanguage.JAVA)

        assertThatThrownBy { underTest.generateCode(request) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generateSharedCode renders one file per kind of shared definition`() {
        // given: a message and an error, and a stubbed shared builder
        val definitions = SharedDefinitions(
            messages = listOf(RootElementDefinition.Message(id = "message_bikeReturned", name = "bikeReturned")),
            errors = listOf(RootElementDefinition.Error(id = "error_damaged", name = "bikeDamaged", code = "DAMAGED")),
        )
        every { kotlinSharedBuilder.render(group = any(), packagePath = PackagePath("packagePath")) } answers {
            SourceCode("content of ${firstArg<SharedDefinitionsGraph.Group>().type.typeName}")
        }

        // when: generating the shared code
        val result = underTest.generateSharedCode(definitions = definitions, outputLanguage = OutputLanguage.KOTLIN, packagePath = "packagePath")

        // then: each kind is rendered into a file named after its type
        assertThat(result).containsExactly(
            sharedFile(fileName = "Messages.kt", content = "content of Messages"),
            sharedFile(fileName = "Errors.kt", content = "content of Errors"),
        )
        verify { kotlinSharedBuilder.render(group = match { it.type == SharedDefinitionType.MESSAGES }, packagePath = PackagePath("packagePath")) }
        verify { kotlinSharedBuilder.render(group = match { it.type == SharedDefinitionType.ERRORS }, packagePath = PackagePath("packagePath")) }
        confirmVerified(kotlinSharedBuilder)
    }

    @Test
    fun `generateSharedCode renders nothing without shared definitions`() {
        val result = underTest.generateSharedCode(definitions = SharedDefinitions(), outputLanguage = OutputLanguage.KOTLIN, packagePath = "packagePath")

        assertThat(result).isEmpty()
        confirmVerified(kotlinSharedBuilder)
    }

    @Test
    fun `generateSharedCode throws when output language is not supported`() {
        assertThatThrownBy { underTest.generateSharedCode(definitions = SharedDefinitions(), outputLanguage = OutputLanguage.JAVA, packagePath = "packagePath") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sharedFile(fileName: String, content: String): GeneratedApiFile = GeneratedApiFile(
        fileName = fileName,
        packagePath = "packagePath",
        content = content,
        language = OutputLanguage.KOTLIN,
        processId = null,
    )
}
