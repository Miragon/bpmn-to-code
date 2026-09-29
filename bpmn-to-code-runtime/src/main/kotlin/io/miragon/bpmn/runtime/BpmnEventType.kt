package io.miragon.bpmn.runtime

/**
 * Event definition of an [Event]: [NONE] without one, [MULTIPLE] with several.
 */
enum class BpmnEventType {
    NONE,
    TIMER,
    MESSAGE,
    ERROR,
    SIGNAL,
    ESCALATION,
    COMPENSATION,
    CONDITIONAL,
    LINK,
    TERMINATE,
    MULTIPLE,
}
