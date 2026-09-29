package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

data class SequenceFlowDefinition(
    val id: String?,
    val sourceRef: String,
    val targetRef: String,
    val flowName: String? = null,
    val conditionExpression: String? = null,
    val isDefault: Boolean = false,
) : VariableMapping<String> {
    override fun getName() = id?.toUpperSnakeCase().orEmpty()
    override fun getValue() = id.orEmpty()
    override fun getRawName() = id.orEmpty()
}
