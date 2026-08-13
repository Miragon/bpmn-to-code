package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * A `bpmn:SequenceFlow`, always emitted inside the scope that owns it.
 */
@Serializable
internal data class SequenceFlowJson(
    val id: String,
    val sourceRef: String,
    val targetRef: String,
    val name: String? = null,
    val conditionExpression: String? = null,
)
