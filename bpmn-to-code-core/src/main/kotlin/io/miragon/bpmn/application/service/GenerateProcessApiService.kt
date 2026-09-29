package io.miragon.bpmn.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService
import io.miragon.bpmn.domain.validation.model.ValidationPhase

class GenerateProcessApiService(
    private val codeGenerator: GenerateApiCodePort = CodeGenerationAdapter(),
    private val bpmnFileLoader: LoadBpmnFilesPort = BpmnFileLoader(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
    private val fileSystemOutput: SaveProcessApiPort = ProcessApiFileSaver(),
) : GenerateProcessApiFromFilesystemUseCase {

    private val logger = KotlinLogging.logger {}

    override fun generateProcessApi(command: GenerateProcessApiFromFilesystemUseCase.Command): List<BpmnFileResult> {
        val validationService = BpmnValidationService(command.validationConfig)
        val inputFiles = bpmnFileLoader.loadFrom(command.baseDir, command.filePattern)
        val extractedModels = inputFiles.map { it to bpmnService.extract(it, command.engine) }
        val executableModels = filterExecutableProcesses(extractedModels)
        val models = executableModels.map { (_, model) -> model }
        validationService.validate(models = models, engine = command.engine, phase = ValidationPhase.PRE_MERGE)
        if (!command.enableVariants) SourcedProcessModel.requireUniqueProcessIds(toSourcedModels(executableModels))
        val mergedModels = ProcessModel.mergeByProcessId(models)
        validationService.validate(models = mergedModels, engine = command.engine, phase = ValidationPhase.POST_MERGE)
        val processFiles = mergedModels.flatMap { codeGenerator.generateCode(toBpmnModelApi(it, command)) }
        val sharedFiles = codeGenerator.generateSharedCode(toSharedDefinitionsApi(mergedModels, command))
        val generatedFiles = (processFiles + sharedFiles).distinctBy { it.packagePath to it.fileName }
        fileSystemOutput.writeFiles(generatedFiles, command.outputFolderPath)
        val filesByProcessId = executableModels
            .groupBy({ (_, model) -> model.processId }, { (file, _) -> file.fileName })
        return mergedModels.map { model ->
            BpmnFileResult(processId = model.processId, sourceFiles = filesByProcessId[model.processId] ?: emptyList())
        }
    }

    private fun toSourcedModels(models: List<Pair<BpmnResource, ProcessModel>>) = models.map { (file, model) -> SourcedProcessModel(file.fileName, model) }

    private fun filterExecutableProcesses(
        extractedModels: List<Pair<BpmnResource, ProcessModel>>,
    ): List<Pair<BpmnResource, ProcessModel>> = extractedModels.filter { (file, model) ->
        val keep = model.isExecutable
        if (!keep) logger.info { "Skipping '${model.processId}' (${file.fileName}): process is marked non-executable" }
        keep
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
