package io.miragon.bpmn.domain.shared

/**
 * The structural kind of a BPMN event. [direction] follows from the shape: end and intermediate-throw
 * events send their event definition as a *result*, every other shape catches it as a *trigger*.
 */
enum class EventShape(val direction: EventDirection) {
    START_EVENT(EventDirection.CATCH),
    END_EVENT(EventDirection.THROW),
    INTERMEDIATE_CATCH_EVENT(EventDirection.CATCH),
    INTERMEDIATE_THROW_EVENT(EventDirection.THROW),
    BOUNDARY_EVENT(EventDirection.CATCH),
}
