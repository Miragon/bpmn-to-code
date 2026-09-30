package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that sends or receives the [message].
 */
interface HasMessage : FlowNode {
    val message: MessageName
}
