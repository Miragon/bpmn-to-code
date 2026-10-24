package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.adapter.outbound.filesystem.BpmnFileLoader
import io.miragon.bpmn.adapter.outbound.filesystem.ProcessApiFileSaver
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.application.port.outbound.SaveProcessApiPort
import io.miragon.bpmn.domain.BpmnFileResult
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService

class GenerateProcessApiService(
    private val codeGenerator: GenerateApiCodePort = CodeGenerationAdapter(),
    private val bpmnFileLoader: LoadBpmnFilesPort = BpmnFileLoader(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
    private val fileSystemOutput: SaveProcessApiPort = ProcessApiFileSaver(),
) : GenerateProcessApiFromFilesystemUseCase {

    override fun generateProcessApi(command: GenerateProcessApiFromFilesystemUseCase.Command): List<BpmnFileResult> {
        val inputFiles = bpmnFileLoader.loadFrom(command.baseDir, command.filePattern)
        val extractedModels = inputFiles.map { SourcedProcessModel(it.fileName, bpmnService.extract(it, command.engine)) }
        val sources = BpmnValidationService(command.validationConfig).processApiSources(sources = extractedModels, engine = command.engine)
        val generatedFiles = codeGenerator.generate(
            ProcessApiRequest(
                models = sources.map { it.model },
                outputLanguage = command.outputLanguage,
                packagePath = command.packagePath,
                engine = command.engine,
            ),
        )
        fileSystemOutput.deleteStaleFiles(
            generatedFiles = generatedFiles,
            outputFolderPath = command.outputFolderPath,
            packagePath = command.packagePath,
        )
        fileSystemOutput.writeFiles(generatedFiles, command.outputFolderPath)
        return sources.map { BpmnFileResult(processId = it.model.processId, sourceFile = it.fileName) }
    }
}
