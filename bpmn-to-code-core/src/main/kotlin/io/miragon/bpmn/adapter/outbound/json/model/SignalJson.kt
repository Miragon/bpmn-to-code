package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class SignalJson(
    val id: String,
    val name: String,
)
