package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

data class MessageDefinition(
    val id: String?,
    private val name: String?,
    val correlationKey: String? = null,
) : VariableMapping<String> {
    override fun getName() = name?.toUpperSnakeCase() ?: ""
    override fun getValue() = name ?: ""
    override fun getRawName() = name ?: ""
    fun hasName() = name != null
}