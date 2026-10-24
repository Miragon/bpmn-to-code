package io.miragon.bpmn.domain

/**
 * A BPMN file that cannot be read: it is not well-formed XML, declares a DOCTYPE, or has no process.
 */
class BpmnReadException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
