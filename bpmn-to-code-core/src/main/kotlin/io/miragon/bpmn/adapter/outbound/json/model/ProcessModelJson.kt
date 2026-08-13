package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of the generated process JSON (format 2.0).
 *
 * The shape is aligned with OMG BPMN 2.0 / `bpmn-moddle`: a scope owns its flow nodes **and** its sequence
 * flows, a node references flows by id, and `bpmn:Definitions` root elements live in a shared registry.
 * See [ADR 018](../../../../../../../../../docs/contributing/adr/018-process-json-v2.md).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ProcessModelJson(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("\$schema") val schema: String = SCHEMA_URL,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val formatVersion: String = FORMAT_VERSION,
    val process: ProcessJson,
    val definitions: DefinitionsJson = DefinitionsJson(),
    val variants: List<VariantJson>? = null,
) {
    companion object {
        const val FORMAT_VERSION = "2.0"
        const val SCHEMA_URL = "https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json"
    }
}
