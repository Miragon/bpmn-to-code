package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.ExtractProcessModelsUseCase
import io.miragon.bpmn.application.service.ExtractProcessModelsService
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

class ExtractProcessModelsPlugin(
    private val useCase: ExtractProcessModelsUseCase = ExtractProcessModelsService(),
) {

    fun execute(resources: List<BpmnResource>, engine: ProcessEngine): List<ProcessModel> = useCase.extractProcessModels(
        ExtractProcessModelsUseCase.Command(resources = resources, engine = engine),
    )
}
