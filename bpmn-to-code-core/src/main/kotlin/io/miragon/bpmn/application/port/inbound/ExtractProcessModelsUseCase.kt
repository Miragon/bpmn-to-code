package io.miragon.bpmn.application.port.inbound

import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * Turns raw BPMN resources into process models, without generating or validating anything.
 *
 * Callers that bring their own resources and drive validation themselves — `bpmn-to-code-testing` does —
 * need the models and nothing else. Without this port their only route was the engine adapter directly,
 * which put a second module inside core's outbound internals.
 */
interface ExtractProcessModelsUseCase {

    fun extractProcessModels(command: Command): List<ProcessModel>

    data class Command(
        val resources: List<BpmnResource>,
        val engine: ProcessEngine,
    )
}
