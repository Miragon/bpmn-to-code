package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class SequenceFlowJson(
    val id: String,
    val sourceRef: String,
    val targetRef: String,
    val name: String? = null,
    val conditionExpression: String? = null,
    val isDefault: Boolean,
)
