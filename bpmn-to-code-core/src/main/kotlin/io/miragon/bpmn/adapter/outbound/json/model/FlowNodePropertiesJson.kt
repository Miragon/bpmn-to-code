package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class FlowNodePropertiesJson(
    val type: String,
    val implementationValue: String? = null,
    val implementationKind: String? = null,
    val calledElement: String? = null,
    val timerType: String? = null,
    val timerValue: String? = null,
    val messageName: String? = null,
    val messageDirection: String? = null,
    val signalName: String? = null,
    val signalDirection: String? = null,
)
