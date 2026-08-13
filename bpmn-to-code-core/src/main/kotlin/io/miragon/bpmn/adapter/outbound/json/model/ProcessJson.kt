package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * The `bpmn:Process` scope: its metadata plus the flow nodes and sequence flows it directly contains.
 */
@Serializable
internal data class ProcessJson(
    val id: String,
    val name: String? = null,
    val isExecutable: Boolean = true,
    val engine: String? = null,
    val flowNodes: List<FlowNodeJson> = emptyList(),
    val sequenceFlows: List<SequenceFlowJson> = emptyList(),
)
