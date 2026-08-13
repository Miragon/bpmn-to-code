package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * A process variable a node reads or writes, with the direction and expression from ADR 015.
 */
@Serializable
internal data class VariableJson(
    val name: String,
    val direction: String,
    val expression: String? = null,
)
