package io.miragon.bpmn.runtime

/**
 * An [Event] that throws or catches the [error].
 */
interface ErrorEvent : Event {
    val error: BpmnErrorDefinition
}
