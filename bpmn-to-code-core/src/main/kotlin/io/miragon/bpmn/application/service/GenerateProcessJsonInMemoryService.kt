package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.adapter.outbound.json.BpmnJsonGenerationAdapter
import io.miragon.bpmn.application.port.inbound.GenerateProcessJsonInMemoryUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateJsonPort
import io.miragon.bpmn.domain.GeneratedJsonFile
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService

class GenerateProcessJsonInMemoryService(
    private val jsonGenerator: GenerateJsonPort = BpmnJsonGenerationAdapter(),
    private val bpmnExtractor: ExtractBpmnPort = ExtractBpmnAdapter(),
) : GenerateProcessJsonInMemoryUseCase {

    override fun generateProcessJson(command: GenerateProcessJsonInMemoryUseCase.Command): List<GeneratedJsonFile> {
        val extractedModels = command.resources.map { SourcedProcessModel(it.fileName, bpmnExtractor.extract(it, command.engine)) }
        val sources = BpmnValidationService(command.validationConfig).validateAndNormalize(
            sources = extractedModels,
            engine = command.engine,
            artifactNameOf = GeneratedJsonFile::nameOf,
        )
        return sources.map { jsonGenerator.generateJson(it.model) }
    }
}
