package io.miragon.bpmn.runtime

/**
 * An [Event] that throws or catches the [escalation].
 */
interface EscalationEvent : Event {
    val escalation: BpmnEscalationDefinition
}
