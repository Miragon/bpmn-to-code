package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter
import io.miragon.bpmn.application.port.inbound.ExtractProcessModelsUseCase
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.domain.ProcessModel

class ExtractProcessModelsService(
    private val bpmnService: ExtractBpmnPort = ExtractBpmnAdapter(),
) : ExtractProcessModelsUseCase {

    override fun extractProcessModels(command: ExtractProcessModelsUseCase.Command): List<ProcessModel> = command.resources.map { bpmnService.extract(it, command.engine) }
}
