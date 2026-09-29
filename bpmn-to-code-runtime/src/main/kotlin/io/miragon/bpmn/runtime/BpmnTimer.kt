package io.miragon.bpmn.runtime

/**
 * A BPMN timer definition.
 *
 * @param type The BPMN timer type.
 * @param timerValue The timer expression (ISO 8601 duration, date, or cycle).
 */
data class BpmnTimer(
    val type: TimerType,
    val timerValue: String,
)
