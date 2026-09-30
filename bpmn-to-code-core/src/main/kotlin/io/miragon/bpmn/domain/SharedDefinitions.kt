package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Identifiers that are not owned by one process — job types, message, signal, error and escalation names the engine
 * resolves across process boundaries, and the names of process variables several processes share. Unlike element ids
 * they are generated once per run instead of once per Process API.
 */
data class SharedDefinitions(
    val serviceTasks: List<ServiceTaskDefinition> = emptyList(),
    val messages: List<RootElementDefinition.Message> = emptyList(),
    val signals: List<RootElementDefinition.Signal> = emptyList(),
    val errors: List<RootElementDefinition.Error> = emptyList(),
    val escalations: List<RootElementDefinition.Escalation> = emptyList(),
    val processVariables: List<VariableDefinition> = emptyList(),
) {

    companion object {
        fun from(models: List<ProcessModel>) = SharedDefinitions(
            serviceTasks = models.flatMap { it.serviceTasks }.distinctByValue(),
            messages = models.flatMap { it.definitions.messages }.distinctByValue(),
            signals = models.flatMap { it.definitions.signals }.distinctByValue(),
            errors = models.flatMap { it.definitions.errors }.distinctByValue(),
            escalations = models.flatMap { it.definitions.escalations }.distinctByValue(),
            processVariables = models.flatMap { it.variables }.distinctByValue(),
        )

        private fun <T : VariableMapping<*>> List<T>.distinctByValue(): List<T> = filter { it.getRawName().isNotEmpty() }
            .distinctBy { it.getValue() }.sortedBy { it.getRawName() }
    }
}
