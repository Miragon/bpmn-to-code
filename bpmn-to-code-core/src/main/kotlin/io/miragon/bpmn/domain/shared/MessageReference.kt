package io.miragon.bpmn.domain.shared

/**
 * A reference to a `bpmn:Message` root element.
 *
 * Carried both by a message event definition and by a send/receive task (`bpmn:ReceiveTask.messageRef`),
 * so correlation-related rules can treat the two uniformly. [messageName] is kept alongside the reference
 * for convenience; everything else about the message — including its correlation key — lives on
 * [RootElementDefinition.Message] in the model's registry.
 */
data class MessageReference(
    val messageRef: String? = null,
    val messageName: String? = null,
)
