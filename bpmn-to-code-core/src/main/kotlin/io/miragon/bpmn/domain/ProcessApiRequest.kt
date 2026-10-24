package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * What one run generates Process APIs from: the validated models and the settings they are rendered with. Every model
 * gets a Process API of its own, and the run gets one set of shared definitions.
 */
data class ProcessApiRequest(
    val models: List<ProcessModel>,
    val outputLanguage: OutputLanguage,
    val packagePath: String,
    val engine: ProcessEngine,
)
