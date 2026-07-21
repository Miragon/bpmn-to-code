package io.miragon.bpmn.domain.shared

/**
 * Whether a message- or signal-bearing node throws (sends) or catches (receives) its event.
 */
enum class EventDirection {
    THROW,
    CATCH,
}
