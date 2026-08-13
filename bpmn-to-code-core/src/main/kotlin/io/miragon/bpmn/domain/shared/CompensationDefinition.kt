package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * A `bpmn:compensateEventDefinition`, keyed by the id of the event node that carries it. [activityRef] is
 * the activity whose compensation handler is triggered — absent when the event compensates its whole scope.
 */
data class CompensationDefinition(
    val id: String?,
    val type: CompensationDefinition.Type,
    val activityRef: String? = null,
    val waitForCompletion: Boolean? = null,
) : VariableMapping<String> {
    override fun getName() = id?.toUpperSnakeCase() ?: ""
    override fun getValue() = id ?: ""
    override fun getRawName() = id ?: ""

    /**
     * Whether the event catches a compensation (boundary) or throws one (intermediate / end).
     */
    enum class Type { CATCHING, THROWING }
}
