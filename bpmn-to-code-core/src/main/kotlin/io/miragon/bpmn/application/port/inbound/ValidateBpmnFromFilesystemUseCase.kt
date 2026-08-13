package io.miragon.bpmn.application.port.inbound

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.model.ValidationConfig

interface ValidateBpmnFromFilesystemUseCase {

    fun validateBpmn(command: Command): ValidationResult

    data class Command(
        val baseDir: String,
        val filePattern: String,
        val engine: ProcessEngine,
        val validationConfig: ValidationConfig = ValidationConfig(),
    )
}
