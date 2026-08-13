package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * The target of a `bpmn:CallActivity` plus the engines' variable-propagation flags.
 */
@Serializable
internal data class CalledElementJson(
    val processId: String? = null,
    val propagateAllInputVariables: Boolean? = null,
    val propagateAllOutputVariables: Boolean? = null,
)
