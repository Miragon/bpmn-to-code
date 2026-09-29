package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.adapter.outbound.filesystem.BpmnFileLoader
import io.miragon.bpmn.adapter.outbound.filesystem.ProcessJsonFileSaver
import io.miragon.bpmn.adapter.outbound.json.BpmnJsonGenerationAdapter
import io.miragon.bpmn.application.port.inbound.GenerateProcessJsonFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateJsonPort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.application.port.outbound.SaveProcessJsonPort
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService

class GenerateProcessJsonService(
    private val jsonGenerator: GenerateJsonPort = BpmnJsonGenerationAdapter(),
    private val bpmnFileLoader: LoadBpmnFilesPort = BpmnFileLoader(),
    private val bpmnExtractor: ExtractBpmnPort = ExtractBpmnAdapter(),
    private val fileSaver: SaveProcessJsonPort = ProcessJsonFileSaver(),
) : GenerateProcessJsonFromFilesystemUseCase {

    override fun generateProcessJson(command: GenerateProcessJsonFromFilesystemUseCase.Command) {
        val inputFiles = bpmnFileLoader.loadFrom(command.baseDir, command.filePattern)
        val sources = inputFiles.map { SourcedProcessModel(it.fileName, bpmnExtractor.extract(it, command.engine)) }
        val mergedModels = BpmnValidationService(command.validationConfig).validateAndMerge(
            sources = sources,
            engine = command.engine,
            enableVariants = command.enableVariants,
        )
        val generatedFiles = mergedModels.map { jsonGenerator.generateJson(it) }
        fileSaver.writeFiles(generatedFiles, command.outputFolderPath)
    }
}
