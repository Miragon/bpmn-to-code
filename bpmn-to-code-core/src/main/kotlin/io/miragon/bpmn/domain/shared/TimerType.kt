package io.miragon.bpmn.domain.shared

/**
 * The kind of `bpmn:timerEventDefinition` child that carries the timer expression.
 *
 * [label] is how the modeler spells the type, as used in validation messages.
 */
enum class TimerType(val label: String) {
    DATE("Date"),
    DURATION("Duration"),
    CYCLE("Cycle"),
}
