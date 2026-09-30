package io.miragon.bpmn.runtime

/**
 * An [Event] with a [timer] definition, so tooling can find every timer without knowing the node types.
 */
interface TimerEvent : Event {
    val timer: BpmnTimer
}
