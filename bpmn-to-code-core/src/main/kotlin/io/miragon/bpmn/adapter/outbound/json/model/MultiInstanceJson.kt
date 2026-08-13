package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * `bpmn:multiInstanceLoopCharacteristics` on an activity, normalised across engines
 * ([#73](https://github.com/Miragon/bpmn-to-code/issues/73)). The expressions are preserved verbatim, so
 * they stay in the engine's own syntax.
 */
@Serializable
internal data class MultiInstanceJson(
    val sequential: Boolean,
    val inputCollection: String? = null,
    val inputElement: String? = null,
    val outputCollection: String? = null,
    val outputElement: String? = null,
    val cardinality: String? = null,
    val completionCondition: String? = null,
)
