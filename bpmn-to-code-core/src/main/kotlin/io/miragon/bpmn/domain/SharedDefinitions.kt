package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition

/**
 * Identifiers the engine resolves across process boundaries — job types, message, signal, error and
 * escalation names. Unlike element ids or variables they are not owned by one process, so they are
 * generated once per run instead of once per Process API.
 */
data class SharedDefinitions(
    val serviceTasks: List<ServiceTaskDefinition> = emptyList(),
    val messages: List<RootElementDefinition.Message> = emptyList(),
    val signals: List<RootElementDefinition.Signal> = emptyList(),
    val errors: List<RootElementDefinition.Error> = emptyList(),
    val escalations: List<RootElementDefinition.Escalation> = emptyList(),
)
