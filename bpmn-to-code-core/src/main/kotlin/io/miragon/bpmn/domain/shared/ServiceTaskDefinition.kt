package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * The service-task-like implementation of one node, as consumed by the generated `ServiceTasks` object.
 * Wraps the typed [TaskImplementation] and exposes its [TaskImplementation.reference] as the constant value.
 */
data class ServiceTaskDefinition(val id: String?, val implementation: TaskImplementation) : VariableMapping<String> {
    override fun getName() = reference?.toUpperSnakeCase().orEmpty()
    override fun getValue() = reference.orEmpty()
    override fun getRawName() = reference.orEmpty()
    fun hasImplementation() = reference != null

    private val reference: String? get() = implementation.reference
}
