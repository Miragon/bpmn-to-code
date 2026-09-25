package io.miragon.bpmn.domain.service

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

class SharedDefinitionsService {

    fun collect(models: List<ProcessModel>) = SharedDefinitions(
        serviceTasks = models.flatMap { it.serviceTasks }.distinctByValue(),
        messages = models.flatMap { it.definitions.messages }.distinctByValue(),
        signals = models.flatMap { it.definitions.signals }.distinctByValue(),
        errors = models.flatMap { it.definitions.errors }.distinctByValue(),
        escalations = models.flatMap { it.definitions.escalations }.distinctByValue(),
    )

    private fun <T : VariableMapping<*>> List<T>.distinctByValue(): List<T> = filter { it.getRawName().isNotEmpty() }
        .distinctBy { it.getValue() }
        .sortedBy { it.getRawName() }
}
