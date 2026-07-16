package io.miragon.bpmn.adapter.outbound.json.model

import kotlinx.serialization.Serializable

@Serializable
data class BpmnModelJson(
    val processId: String,
    val flowNodes: List<FlowNodeJson> = emptyList(),
    val messages: List<MessageJson> = emptyList(),
    val signals: List<SignalJson>,
    val errors: List<ErrorJson>,
    val escalations: List<EscalationJson> = emptyList(),
    val compensations: List<CompensationJson> = emptyList(),
    val sequenceFlows: List<SequenceFlowJson> = emptyList(),
    val variants: List<VariantJson>? = null,
)
