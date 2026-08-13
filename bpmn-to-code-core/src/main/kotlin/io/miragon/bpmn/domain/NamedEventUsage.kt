package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.shared.FlowNodeDefinition

/**
 * One node's use of a named `bpmn:Definitions` root element — a message or a signal — together with the
 * throw/catch role it plays. Correlation rules reason over these rather than over node types, so a
 * message catch event and a receive task are treated uniformly.
 *
 * Produced by [ProcessModel.messageUsages] and [ProcessModel.signalUsages].
 */
data class NamedEventUsage(
    val node: FlowNodeDefinition,
    val name: String,
    val direction: EventDirection,
)
