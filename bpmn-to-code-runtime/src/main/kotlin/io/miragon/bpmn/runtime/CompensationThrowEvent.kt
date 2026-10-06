package io.miragon.bpmn.runtime

/**
 * An [Event] that throws a compensation: an intermediate throw event or an end event with a compensate definition.
 */
interface CompensationThrowEvent : Event
