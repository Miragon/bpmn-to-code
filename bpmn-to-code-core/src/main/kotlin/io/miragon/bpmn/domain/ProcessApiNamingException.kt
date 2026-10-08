package io.miragon.bpmn.domain

/**
 * BPMN files whose generated artifacts cannot be named: a variant name is malformed, or several files would be
 * generated under one name.
 */
class ProcessApiNamingException(message: String) : IllegalArgumentException(message)
