package io.miragon.bpmn.web.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.adapter.inbound.CreateProcessJsonInMemoryPlugin
import io.miragon.bpmn.domain.GeneratedJsonFile
import io.miragon.bpmn.web.model.BpmnFileData
import io.miragon.bpmn.web.model.GenerateJsonRequest
import io.miragon.bpmn.web.model.GenerateJsonResponse

class WebJsonGenerationService {

    private val logger = KotlinLogging.logger {}

    private val plugin = CreateProcessJsonInMemoryPlugin()

    fun generate(request: GenerateJsonRequest): GenerateJsonResponse {
        val config = request.config
        logger.info { "Generating JSON for ${request.files.size} file(s) [${config.processEngine}]" }
        return GenerationGuard.run(files = request.files, failure = GenerateJsonResponse::failure) {
            val bpmnInputs = request.files.map { buildInput(it) }
            val jsonFiles = plugin.execute(bpmnContents = bpmnInputs, engine = config.processEngine)
            val responseFiles = jsonFiles.map { mapToResponse(it) }
            GenerateJsonResponse(success = true, files = responseFiles)
        }
    }

    private fun buildInput(file: BpmnFileData) = CreateProcessJsonInMemoryPlugin.BpmnInput(bpmnXml = file.bpmnXml(), processName = file.processName())

    private fun mapToResponse(jsonFile: GeneratedJsonFile) = GenerateJsonResponse.GeneratedJsonFileResponse(
        fileName = jsonFile.fileName,
        content = jsonFile.content,
        processId = jsonFile.processId,
    )
}
