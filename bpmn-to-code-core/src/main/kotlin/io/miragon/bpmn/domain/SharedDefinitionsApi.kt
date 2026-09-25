package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.OutputLanguage

/**
 * [SharedDefinitions] together with the code-generation settings they are rendered with.
 */
data class SharedDefinitionsApi(
    val definitions: SharedDefinitions,
    val outputLanguage: OutputLanguage,
    val packagePath: String,
)
