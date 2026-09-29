package io.miragon.bpmn.domain.shared

/**
 * A timer event definition, keyed by the id of the event node that carries it — unlike messages, signals
 * and errors, `bpmn:timerEventDefinition` is not a `bpmn:Definitions` root element.
 */
data class TimerDefinition(
    val id: String?,
    val type: TimerType?,
    val expression: String?,
) {
    fun hasTimerType() = type != null && expression != null
}
