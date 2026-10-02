package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.OutputLanguage
import java.io.File

/**
 * [processId] is `null` for files generated once for all processes, such as `ServiceTasks`.
 */
data class GeneratedApiFile(
    val fileName: String,
    val packagePath: String,
    val content: String,
    val language: OutputLanguage,
    val processId: String?,
) {
    companion object {
        fun packageDirectoryOf(packagePath: String): String = packagePath.replace('.', File.separatorChar)
    }
}
