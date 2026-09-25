package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.testProcessModelApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CodeGenerationAdapterTest {

    private val kotlinProcessBuilder = mockk<CodeGenerationAdapter.AbstractProcessApiBuilder<*>>(relaxed = true)
    private val kotlinSharedBuilder = mockk<CodeGenerationAdapter.AbstractSharedDefinitionsBuilder>(relaxed = true)
    private val underTest = CodeGenerationAdapter(
        processApiBuilders = mapOf(OutputLanguage.KOTLIN to kotlinProcessBuilder),
        sharedDefinitionsBuilders = mapOf(OutputLanguage.KOTLIN to kotlinSharedBuilder),
    )

    @Test
    fun `generateCode delegates to the process api builder and returns its file`() {
        // given: a model API and a stubbed process builder response
        val modelApi = testProcessModelApi()
        val processFile = GeneratedApiFile(
            fileName = "TestApi.kt",
            packagePath = "packagePath",
            content = "content",
            language = OutputLanguage.KOTLIN,
            processId = "test",
        )
        every { kotlinProcessBuilder.buildApiFile(modelApi) } returns processFile

        // when: generating code for a Kotlin model
        val result = underTest.generateCode(modelApi)

        // then: the process builder is called and its file returned
        verify { kotlinProcessBuilder.buildApiFile(modelApi) }
        assertThat(result).isEqualTo(listOf(processFile))
        confirmVerified(kotlinProcessBuilder)
    }

    @Test
    fun `generateCode throws when output language is not supported`() {
        // given: a model API with an unsupported language
        val modelApi = testProcessModelApi(language = OutputLanguage.JAVA)

        // when / then: an exception is thrown
        assertThatThrownBy { underTest.generateCode(modelApi) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generateSharedCode delegates to the shared definitions builder and returns its files`() {
        // given: shared definitions and a stubbed shared builder response
        val api = SharedDefinitionsApi(SharedDefinitions(), OutputLanguage.KOTLIN, "packagePath")
        val sharedFile = GeneratedApiFile(
            fileName = "ServiceTasks.kt",
            packagePath = "packagePath",
            content = "content",
            language = OutputLanguage.KOTLIN,
            processId = null,
        )
        every { kotlinSharedBuilder.buildApiFiles(api) } returns listOf(sharedFile)

        // when: generating the shared code
        val result = underTest.generateSharedCode(api)

        // then: the shared builder's files are returned
        assertThat(result).containsExactly(sharedFile)
    }

    @Test
    fun `generateSharedCode throws when output language is not supported`() {
        // given: shared definitions with an unsupported language
        val api = SharedDefinitionsApi(SharedDefinitions(), OutputLanguage.JAVA, "packagePath")

        // when / then: an exception is thrown
        assertThatThrownBy { underTest.generateSharedCode(api) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
