package io.miragon.bpmn.web.service

import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.web.model.GenerateJsonRequest
import io.miragon.bpmn.web.model.GenerateRequest
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class GeneratorWarmUpTest {

    private val generationService = mockk<WebGenerationService>(relaxed = true)
    private val jsonGenerationService = mockk<WebJsonGenerationService>(relaxed = true)

    private val underTest = GeneratorWarmUp(
        generationService = generationService,
        jsonGenerationService = jsonGenerationService,
        rounds = 2,
    )

    @Test
    fun `generates every bundled example with its own engine in every output language, once per round`() {
        // given
        val apiRequests = mutableListOf<GenerateRequest>()
        val jsonRequests = mutableListOf<GenerateJsonRequest>()

        // when
        underTest.run()

        // then
        verify { generationService.generate(capture(apiRequests)) }
        verify { jsonGenerationService.generate(capture(jsonRequests)) }
        confirmVerified(generationService, jsonGenerationService)
        val apiGenerations = apiRequests.map { request ->
            Generation(
                fileName = request.files.single().fileName,
                engine = request.config.processEngine,
                language = request.config.outputLanguage,
            )
        }
        val twoRounds = everyExampleInEveryLanguage + everyExampleInEveryLanguage
        assertThat(apiGenerations).containsExactlyInAnyOrderElementsOf(twoRounds)
        val jsonGenerations = jsonRequests.map { it.files.single().fileName to it.config.processEngine }
        assertThat(jsonGenerations).containsExactlyElementsOf(examples + examples)
    }

    @Test
    fun `sends the bundled example files unchanged`() {
        // given
        val apiRequests = mutableListOf<GenerateRequest>()

        // when
        underTest.run()

        // then
        verify { generationService.generate(capture(apiRequests)) }
        confirmVerified(generationService)
        val sentFile = apiRequests.first().files.single()
        val bundledStream = javaClass.classLoader.getResourceAsStream("examples/${sentFile.fileName}")
        val bundledFile = requireNotNull(bundledStream).readBytes()
        assertThat(Base64.getDecoder().decode(sentFile.content)).isEqualTo(bundledFile)
    }

    private data class Generation(val fileName: String, val engine: ProcessEngine, val language: OutputLanguage)

    private val examples = listOf(
        "zeebe-bike-leasing.bpmn" to ProcessEngine.ZEEBE,
        "c7-bike-leasing.bpmn" to ProcessEngine.CAMUNDA_7,
        "operaton-bike-leasing.bpmn" to ProcessEngine.OPERATON,
    )

    private val everyExampleInEveryLanguage = examples.flatMap { (fileName, engine) ->
        OutputLanguage.entries.map { language -> Generation(fileName = fileName, engine = engine, language = language) }
    }
}
