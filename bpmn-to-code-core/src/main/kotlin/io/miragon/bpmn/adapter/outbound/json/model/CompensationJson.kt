package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class CompensationJson(
    val id: String,
    val activityRef: String,
)
