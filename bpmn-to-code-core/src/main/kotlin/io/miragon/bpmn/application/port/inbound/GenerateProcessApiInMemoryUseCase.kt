package io.miragon.bpmn.application.port.inbound

import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig

interface GenerateProcessApiInMemoryUseCase {
    fun generateProcessApi(command: Command): List<GeneratedApiFile>

    data class Command(
        val resources: List<BpmnResource>,
        val packagePath: String,
        val outputLanguage: OutputLanguage,
        val engine: ProcessEngine,
        val validationConfig: ValidationConfig = ValidationConfig(),
        val enableVariants: Boolean = false,
    )
}
