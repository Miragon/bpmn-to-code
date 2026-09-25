package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * [processId] is `null` for files generated once for all processes, such as `ServiceTasks`.
 */
data class GeneratedApiFile(
    val fileName: String,
    val packagePath: String,
    val content: String,
    val language: OutputLanguage,
    val processId: String?,
)
