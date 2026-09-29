package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.GenerateProcessApiInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessApiInMemoryService
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig

class CreateProcessApiInMemoryPlugin(
    private val useCase: GenerateProcessApiInMemoryUseCase = GenerateProcessApiInMemoryService(),
) {

    fun execute(
        bpmnContents: List<BpmnInput>,
        packagePath: String,
        outputLanguage: OutputLanguage,
        engine: ProcessEngine,
        validationConfig: ValidationConfig = ValidationConfig(),
        enableVariants: Boolean = false,
    ): List<GeneratedApiFile> = useCase.generateProcessApi(
        GenerateProcessApiInMemoryUseCase.Command(
            packagePath = packagePath,
            outputLanguage = outputLanguage,
            engine = engine,
            validationConfig = validationConfig,
            enableVariants = enableVariants,
            resources = bpmnContents.map { BpmnResource(fileName = it.processName, content = it.bpmnXml.encodeToByteArray()) },
        ),
    )

    data class BpmnInput(val bpmnXml: String, val processName: String)
}
