package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * A [ProcessModel] together with the code-generation settings it is rendered with.
 *
 * [targetEngine] is the engine the caller asked to generate for. It is deliberately *not* the same as
 * [ProcessModel.detectedEngine], which is what the BPMN file itself declares — comparing the two is what
 * `EngineMismatchRule` does.
 */
data class BpmnModelApi(
    val model: ProcessModel,
    val outputLanguage: OutputLanguage,
    val packagePath: String,
    val targetEngine: ProcessEngine,
) {

    fun fileName(): String {
        val separatedApiName = model.apiName.split("_", "-")
        val apiName = separatedApiName.joinToString("") { it.camelCase() }
        return "${apiName}ProcessApi"
    }

    private fun String.camelCase() = replaceFirstChar { it.uppercase() }
}
