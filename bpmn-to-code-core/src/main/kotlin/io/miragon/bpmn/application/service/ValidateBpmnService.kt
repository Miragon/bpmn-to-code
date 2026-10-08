package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.adapter.outbound.filesystem.BpmnFileLoader
import io.miragon.bpmn.application.port.inbound.ValidateBpmnFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.domain.service.BpmnValidationService
import io.miragon.bpmn.domain.validation.ValidationResult
import io.miragon.bpmn.domain.validation.model.Severity

class ValidateBpmnService(
    private val bpmnFileLoader: LoadBpmnFilesPort = BpmnFileLoader(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : ValidateBpmnFromFilesystemUseCase {

    override fun validateBpmn(command: ValidateBpmnFromFilesystemUseCase.Command): ValidationResult {
        val validationService = BpmnValidationService(command.validationConfig)
        val inputFiles = bpmnFileLoader.loadFrom(command.baseDir, command.filePattern)
        val models = inputFiles.map { bpmnService.extract(it, command.engine) }
        val singleModelViolations = validationService.collectSingleModelViolations(models = models, engine = command.engine)
        if (singleModelViolations.any { it.severity == Severity.ERROR }) {
            return ValidationResult(singleModelViolations)
        }
        val crossModelViolations = validationService.collectCrossModelViolations(models = models, engine = command.engine)
        return ValidationResult(singleModelViolations + crossModelViolations)
    }
}
