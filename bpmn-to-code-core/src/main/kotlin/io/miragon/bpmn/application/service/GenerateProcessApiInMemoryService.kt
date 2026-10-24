package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.codegen.CodeGenerationAdapter
import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.GenerateApiCodePort
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessApiRequest
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService

class GenerateProcessApiInMemoryService(
    private val codeGenerator: GenerateApiCodePort = CodeGenerationAdapter(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : GenerateProcessApiInMemoryUseCase {

    override fun generateProcessApi(command: GenerateProcessApiInMemoryUseCase.Command): List<GeneratedApiFile> {
        val extractedModels = command.resources.map { SourcedProcessModel(it.fileName, bpmnService.extract(it, command.engine)) }
        val sources = BpmnValidationService(command.validationConfig).processApiSources(sources = extractedModels, engine = command.engine)
        return codeGenerator.generate(
            ProcessApiRequest(
                models = sources.map { it.model },
                outputLanguage = command.outputLanguage,
                packagePath = command.packagePath,
                engine = command.engine,
            ),
        )
    }
}
