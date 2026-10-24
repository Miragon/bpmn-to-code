package io.miragon.bpmn.application.port.inbound

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.model.ValidationConfig

/**
 * Checks the BPMN files the way the generation checks them, without generating: warnings are logged, and a model the
 * generation would reject throws its [io.miragon.bpmn.domain.RejectedModelException].
 */
interface ValidateBpmnFromFilesystemUseCase {

    fun validateBpmn(command: Command)

    data class Command(
        val baseDir: String,
        val filePattern: String,
        val engine: ProcessEngine,
        val validationConfig: ValidationConfig = ValidationConfig(),
    )
}
