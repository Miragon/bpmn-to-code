package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.SharedDefinitionsApi
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService

class GenerateProcessApiInMemoryService(
    private val codeGenerator: GenerateApiCodePort = CodeGenerationAdapter(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : GenerateProcessApiInMemoryUseCase {

    override fun generateProcessApi(command: GenerateProcessApiInMemoryUseCase.Command): List<GeneratedApiFile> {
        val extractedModels = command.resources.map { SourcedProcessModel(it.fileName, bpmnService.extract(it, command.engine)) }
        val executableSources = SourcedProcessModel.executableOnly(extractedModels)
        val sources = BpmnValidationService(command.validationConfig).validateAndNormalize(
            sources = executableSources,
            engine = command.engine,
            artifactNameOf = { toModelApi(command, it).fileName() },
        )
        val models = sources.map { it.model }
        val processFiles = models.flatMap { codeGenerator.generateCode(toModelApi(command, it)) }
        val sharedFiles = codeGenerator.generateSharedCode(toSharedDefinitionsApi(command, models))
        return processFiles + sharedFiles
    }

    private fun toModelApi(command: GenerateProcessApiInMemoryUseCase.Command, model: ProcessModel) = BpmnModelApi(
        model = model,
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
        targetEngine = command.engine,
    )

    private fun toSharedDefinitionsApi(
        command: GenerateProcessApiInMemoryUseCase.Command,
        models: List<ProcessModel>,
    ) = SharedDefinitionsApi(
        definitions = SharedDefinitions.from(models),
        outputLanguage = command.outputLanguage,
        packagePath = command.packagePath,
    )
}
