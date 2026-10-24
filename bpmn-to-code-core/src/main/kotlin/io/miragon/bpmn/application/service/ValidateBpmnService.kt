package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.adapter.outbound.filesystem.BpmnFileLoader
import io.miragon.bpmn.application.port.inbound.ValidateBpmnFromFilesystemUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.domain.SourcedProcessModel
import io.miragon.bpmn.domain.service.BpmnValidationService
import io.miragon.bpmn.domain.validation.ValidationResult

class ValidateBpmnService(
    private val bpmnFileLoader: LoadBpmnFilesPort = BpmnFileLoader(),
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : ValidateBpmnFromFilesystemUseCase {

    override fun validateBpmn(command: ValidateBpmnFromFilesystemUseCase.Command): ValidationResult {
        val inputFiles = bpmnFileLoader.loadFrom(command.baseDir, command.filePattern)
        val sources = inputFiles.map { SourcedProcessModel(it.fileName, bpmnService.extract(it, command.engine)) }
        return BpmnValidationService(command.validationConfig).validateProcessApiSources(sources = sources, engine = command.engine)
    }
}
