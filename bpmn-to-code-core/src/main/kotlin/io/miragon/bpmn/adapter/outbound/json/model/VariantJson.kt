package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class VariantJson(
    val variantName: String,
    val flowNodes: List<FlowNodeJson>,
    val sequenceFlows: List<SequenceFlowJson>,
)
