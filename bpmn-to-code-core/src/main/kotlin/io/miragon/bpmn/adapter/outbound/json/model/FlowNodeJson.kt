package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FlowNodeJson(
    val id: String,
    val displayName: String? = null,
    val elementType: String,
    val parentId: String? = null,
    val attachedToRef: String? = null,
    val attachedElements: List<String> = emptyList(),
    val previousElements: List<String> = emptyList(),
    val followingElements: List<String> = emptyList(),
    val variables: List<String> = emptyList(),
    val properties: FlowNodePropertiesJson? = null,
    val engineSpecificProperties: Map<String, JsonElement> = emptyMap(),
)
