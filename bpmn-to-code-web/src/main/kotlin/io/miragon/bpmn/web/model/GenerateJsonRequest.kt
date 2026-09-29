package io.miragon.bpmn.web.model

import io.miragon.bpmn.domain.shared.ProcessEngine
import kotlinx.serialization.Serializable

@Serializable
data class GenerateJsonRequest(val files: List<BpmnFileData>, val config: JsonGenerationConfig) {

    @Serializable
    data class JsonGenerationConfig(val processEngine: ProcessEngine, val enableVariants: Boolean = false)
}
