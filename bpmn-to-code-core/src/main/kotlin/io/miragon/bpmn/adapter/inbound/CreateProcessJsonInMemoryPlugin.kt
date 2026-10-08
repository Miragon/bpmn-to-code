package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.GenerateProcessJsonInMemoryUseCase
import io.miragon.bpmn.application.service.GenerateProcessJsonInMemoryService
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedJsonFile
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig

class CreateProcessJsonInMemoryPlugin(
    private val useCase: GenerateProcessJsonInMemoryUseCase = GenerateProcessJsonInMemoryService(),
) {

    fun execute(
        bpmnContents: List<BpmnInput>,
        engine: ProcessEngine,
        validationConfig: ValidationConfig = ValidationConfig(),
    ): List<GeneratedJsonFile> = useCase.generateProcessJson(
        GenerateProcessJsonInMemoryUseCase.Command(
            engine = engine,
            validationConfig = validationConfig,
            resources = bpmnContents.map { BpmnResource(fileName = it.processName, content = it.bpmnXml.encodeToByteArray()) },
        ),
    )

    data class BpmnInput(val bpmnXml: String, val processName: String)
}
