package io.miragon.bpmn.domain.shared

/**
 * The kind of `bpmn:timerEventDefinition` child that carries the timer expression.
 *
 * [label] is the BPMN-flavoured spelling used in the generated `BpmnTimer` constants — a code-generation
 * concern that still lives here because [TimerDefinition] exposes it through [VariableMapping].
 */
enum class TimerType(val label: String) {
    DATE("Date"),
    DURATION("Duration"),
    CYCLE("Cycle"),
}
