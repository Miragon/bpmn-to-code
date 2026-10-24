package io.miragon.bpmn.domain

/**
 * BPMN files the generator rejects as given: the message says what to change in them. Every entry point reports it as
 * the user's to fix, the web app with status 400, and any other exception as a failure of the generator.
 */
abstract class RejectedModelException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
