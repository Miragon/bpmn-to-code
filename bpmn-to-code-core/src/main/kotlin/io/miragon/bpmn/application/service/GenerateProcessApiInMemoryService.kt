package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.service.BpmnValidationService
import io.miragon.bpmn.domain.service.ModelMergerService
import io.miragon.bpmn.domain.service.SharedDefinitionsService
import io.miragon.bpmn.domain.validation.model.ValidationPhase

class GenerateProcessApiInMemoryService(
    private val codeGenerator: GenerateApiCodePort = CodeGenerationAdapter(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : GenerateProcessApiInMemoryUseCase {

    private val modelMergerService = ModelMergerService()
    private val sharedDefinitionsService = SharedDefinitionsService()

    override fun generateProcessApi(
        command: GenerateProcessApiInMemoryUseCase.Command,
    ): List<GeneratedApiFile> {
        val validationService = BpmnValidationService(command.validationConfig)
        val modelsAsFiles = toBpmnFiles(command)
        val models = modelsAsFiles.map { bpmnService.extract(it, command.engine) }
        validationService.validate(models, command.engine, ValidationPhase.PRE_MERGE)
        val mergedModels = modelMergerService.mergeModels(models)
        validationService.validate(mergedModels, command.engine, ValidationPhase.POST_MERGE)
        val processFiles = mergedModels.flatMap { codeGenerator.generateCode(toModelApi(command, it)) }
        val sharedFiles = codeGenerator.generateSharedCode(toSharedDefinitionsApi(command, mergedModels))
        return (processFiles + sharedFiles).distinctBy { it.packagePath to it.fileName }
    }

    private fun toModelApi(
        command: GenerateProcessApiInMemoryUseCase.Command,
        model: ProcessModel,
    ) = BpmnModelApi(
        model = model,
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
        targetEngine = command.engine,
    )

    private fun toSharedDefinitionsApi(
        command: GenerateProcessApiInMemoryUseCase.Command,
        models: List<ProcessModel>,
    ) = SharedDefinitionsApi(
        definitions = sharedDefinitionsService.collect(models),
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
    )

    private fun toBpmnFiles(
        command: GenerateProcessApiInMemoryUseCase.Command,
    ) = command.bpmnContents.map {
        BpmnResource(
            fileName = it.processName,
            content = it.bpmnXml.encodeToByteArray(),
        )
    }
}
