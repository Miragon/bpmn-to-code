package io.miragon.bpmn.domain.shared

/**
 * Zeebe marks a value as a FEEL expression with a leading `=`.
 * Such a value is resolved at runtime, so it has no fixed value to generate a constant for.
 */
object Feel {
    fun isExpression(value: String): Boolean = value.startsWith("=")
}
