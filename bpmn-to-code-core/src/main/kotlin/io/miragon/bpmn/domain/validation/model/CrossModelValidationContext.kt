package io.miragon.bpmn.domain.validation.model

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * Context handed to a [io.miragon.bpmn.domain.validation.CrossModelValidationRule]. Carries every
 * loaded process model and resolves cross-process references such as a call activity's called element.
 */
data class CrossModelValidationContext(
    val models: List<ProcessModel>,
    val engine: ProcessEngine,
) {

    // Process ids are unique after merging (ModelMergerService returns one model per id).
    private val byProcessId = models.associateBy { it.processId }

    /**
     * Returns the model with the given process id, or `null` if no such model was loaded.
     */
    fun findProcess(processId: String): ProcessModel? {
        return byProcessId[processId]
    }

    /**
     * Resolves a call activity's called element to the model of the called process,
     * or `null` if the call activity has no called element or it references an unknown process.
     */
    fun resolveCalledModel(callActivity: CallActivityDefinition): ProcessModel? {
        val ref = callActivity.getValue()
        return if (ref.isBlank()) null else findProcess(ref)
    }
}
