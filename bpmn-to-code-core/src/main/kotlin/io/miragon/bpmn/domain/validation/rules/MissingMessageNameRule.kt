package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags message-bearing nodes whose message carries no 'name' — a message event definition or a
 * send/receive task that cannot be correlated against.
 */
class MissingMessageNameRule : SingleModelValidationRule {

    override val id = "missing-message-name"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> = context.model.allFlowNodes
        .filter { it.hasNamelessMessage() }
        .map { node ->
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = node.id,
                processId = context.model.processId,
                message = "Message element is missing a 'name' attribute.",
            )
        }

    private fun FlowNodeDefinition.hasNamelessMessage(): Boolean = when (this) {
        is FlowNodeDefinition.Event ->
            eventDefinitions.filterIsInstance<EventDefinitionInstance.Message>()
                .any { it.reference.messageRef != null && it.reference.messageName == null }

        is FlowNodeDefinition.Activity.Task ->
            kind in messageTaskKinds && message != null && message.messageName == null

        else -> false
    }

    private companion object {
        val messageTaskKinds = setOf(
            TaskKind.RECEIVE,
            TaskKind.SEND,
        )
    }
}
