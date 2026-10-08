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
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
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
        val executableSources = SourcedProcessModel.executableOnly(extractedModels)
        val sources = BpmnValidationService(command.validationConfig).validateAndNormalize(
            sources = executableSources,
            engine = command.engine,
            artifactNameOf = { toBpmnModelApi(it, command).fileName() },
        )
        val models = sources.map { it.model }
        val processFiles = models.flatMap { codeGenerator.generateCode(toBpmnModelApi(it, command)) }
        val sharedFiles = codeGenerator.generateSharedCode(toSharedDefinitionsApi(models, command))
        val generatedFiles = processFiles + sharedFiles
        fileSystemOutput.deleteStaleFiles(
            generatedFiles = generatedFiles,
            outputFolderPath = command.outputFolderPath,
            packagePath = command.packagePath,
        )
        fileSystemOutput.writeFiles(generatedFiles, command.outputFolderPath)
        return sources.map { BpmnFileResult(processId = it.model.processId, sourceFiles = listOf(it.fileName)) }
    }

    private fun toBpmnModelApi(
        model: ProcessModel,
        command: GenerateProcessApiFromFilesystemUseCase.Command,
    ) = BpmnModelApi(
        model = model,
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
        targetEngine = command.engine,
    )

    private fun toSharedDefinitionsApi(
        models: List<ProcessModel>,
        command: GenerateProcessApiFromFilesystemUseCase.Command,
    ) = SharedDefinitionsApi(
        definitions = SharedDefinitions.from(models),
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
    )
}
