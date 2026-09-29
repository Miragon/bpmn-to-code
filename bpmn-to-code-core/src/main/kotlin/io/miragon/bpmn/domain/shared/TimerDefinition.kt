package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * A timer event definition, keyed by the id of the event node that carries it — unlike messages, signals
 * and errors, `bpmn:timerEventDefinition` is not a `bpmn:Definitions` root element.
 */
data class TimerDefinition(
    val id: String?,
    val type: TimerType?,
    val expression: String?,
) : VariableMapping<Pair<String, String>> {
    override fun getName() = id?.toUpperSnakeCase().orEmpty()
    override fun getValue() = type?.label.orEmpty() to expression.orEmpty()
    override fun getRawName() = id.orEmpty()
    fun hasTimerType() = type != null && expression != null
}
