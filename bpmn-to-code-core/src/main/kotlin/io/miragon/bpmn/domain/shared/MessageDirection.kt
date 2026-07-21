package io.miragon.bpmn.domain.shared

/**
 * Whether a message-bearing node throws (sends) or catches (receives) its message.
 */
enum class MessageDirection {
    THROW,
    CATCH,
}
