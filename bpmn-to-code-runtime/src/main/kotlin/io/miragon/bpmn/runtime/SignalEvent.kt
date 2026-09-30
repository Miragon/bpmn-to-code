package io.miragon.bpmn.runtime

/**
 * An [Event] that throws or catches the [signal].
 */
interface SignalEvent : Event {
    val signal: SignalName
}
