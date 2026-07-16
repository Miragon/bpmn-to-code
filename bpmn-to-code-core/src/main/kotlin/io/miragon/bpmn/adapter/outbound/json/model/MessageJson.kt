package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageJson(
    val id: String,
    val name: String,
)
