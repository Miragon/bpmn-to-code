package io.miragon.bpmn.domain.shared

data class SequenceFlowDefinition(
    val id: String?,
    val sourceRef: String,
    val targetRef: String,
    val flowName: String? = null,
    val conditionExpression: String? = null,
    val isDefault: Boolean = false,
)
