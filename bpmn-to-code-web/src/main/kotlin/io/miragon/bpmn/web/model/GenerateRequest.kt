package io.miragon.bpmn.web.model

import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequest(val files: List<BpmnFileData>, val config: GenerationConfig) {

    @Serializable
    data class GenerationConfig(
        val outputLanguage: OutputLanguage,
        val processEngine: ProcessEngine,
        val enableVariants: Boolean = false,
    )
}
