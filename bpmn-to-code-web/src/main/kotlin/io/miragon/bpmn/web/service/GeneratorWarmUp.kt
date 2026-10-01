package io.miragon.bpmn.web.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateJsonRequest
import io.miragon.bpmn.web.model.GenerateRequest
import java.util.Base64
import kotlin.time.measureTime

/**
 * Runs once at start-up, not periodically: generates the bundled examples in every output language and as JSON,
 * [rounds] times in a row, and discards the results.
 *
 * The JVM runs new code slowly and compiles it to fast machine code only once it has run often enough. The web app
 * serves too few requests to get there by itself, so without this every request stays slow. The warm-up does the
 * "often enough" up front, and compiled code stays compiled for as long as the app runs.
 */
class GeneratorWarmUp(
    private val generationService: WebGenerationService = WebGenerationService(),
    private val jsonGenerationService: WebJsonGenerationService = WebJsonGenerationService(),
    private val rounds: Int = 5,
) {

    private val logger = KotlinLogging.logger {}

    private val bundledExamples = listOf(
        "zeebe-bike-leasing.bpmn" to ProcessEngine.ZEEBE,
        "c7-bike-leasing.bpmn" to ProcessEngine.CAMUNDA_7,
        "operaton-bike-leasing.bpmn" to ProcessEngine.OPERATON,
    )

    fun run() {
        val examples = bundledExamples.map { (fileName, engine) -> Example(file = bundled(fileName), engine = engine) }
        logger.info { "Warming up the generator with ${examples.size} example models" }
        val duration = measureTime {
            repeat(rounds) { examples.forEach { generate(it) } }
        }
        logger.info { "Warmed up the generator in $duration" }
    }

    private fun generate(example: Example) {
        OutputLanguage.entries.forEach { language ->
            val config = GenerateRequest.GenerationConfig(outputLanguage = language, processEngine = example.engine)
            generationService.generate(GenerateRequest(files = listOf(example.file), config = config))
        }
        val jsonConfig = GenerateJsonRequest.JsonGenerationConfig(processEngine = example.engine)
        jsonGenerationService.generate(GenerateJsonRequest(files = listOf(example.file), config = jsonConfig))
    }

    private fun bundled(fileName: String): BpmnFileData {
        val resource = "examples/$fileName"
        val stream = javaClass.classLoader.getResourceAsStream(resource)
        checkNotNull(stream) { "Missing bundled example $resource" }
        val content = stream.use { Base64.getEncoder().encodeToString(it.readBytes()) }
        return BpmnFileData(fileName = fileName, content = content)
    }

    private data class Example(val file: BpmnFileData, val engine: ProcessEngine)
}
