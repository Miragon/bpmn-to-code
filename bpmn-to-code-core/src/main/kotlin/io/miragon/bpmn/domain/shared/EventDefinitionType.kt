package io.miragon.bpmn.domain.shared

/**
 * The kind of BPMN event definition carried by an [BpmnNodeType.Event].
 *
 * Per the BPMN 2.0 (OMG) spec an event definition acts as a *trigger* on catching events
 * (start, intermediate-catch, boundary) and describes a *result* on throwing events
 * (intermediate-throw, end). This enum captures that shared definition kind for both roles.
 */
enum class EventDefinitionType {
    TIMER,
    MESSAGE,
    ERROR,
    SIGNAL,
    ESCALATION,
    COMPENSATION,
    NONE,
}
