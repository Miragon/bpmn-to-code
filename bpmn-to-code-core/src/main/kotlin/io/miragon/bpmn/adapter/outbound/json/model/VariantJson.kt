package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

/**
 * One process variant of a merged model — the same process id modelled in several BPMN files.
 */
@Serializable
internal data class VariantJson(
    val name: String,
    val flowNodes: List<FlowNodeJson> = emptyList(),
    val sequenceFlows: List<SequenceFlowJson> = emptyList(),
)
