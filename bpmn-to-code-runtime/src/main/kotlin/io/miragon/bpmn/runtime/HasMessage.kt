package io.miragon.bpmn.runtime

/**
 * A [FlowNode] that sends or receives the [message].
 *
 * [correlationKey] is the correlation-key expression of the message as modelled, e.g. `=applicationId` in Zeebe. It is
 * set on nodes where a process instance waits for the message and `null` everywhere else: on nodes that send it, on
 * message start events of the process and on every node of Camunda 7 and Operaton.
 */
interface HasMessage : FlowNode {
    val message: MessageName
    val correlationKey: String? get() = null
}
