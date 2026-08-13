package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A verbatim projection of one foreign-namespace element below `bpmn:extensionElements`.
 *
 * [type] keeps the namespace prefix (`zeebe:taskHeaders`, `camunda:properties`) so provenance is never
 * lost, and the structure nests arbitrarily. This is the escape hatch for engine data bpmn-to-code does
 * not normalise — a new engine feature appears here without a schema change.
 */
@Serializable
internal data class ExtensionJson(
    @SerialName("\$type") val type: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<ExtensionJson> = emptyList(),
    val body: String? = null,
)
