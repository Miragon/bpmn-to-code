package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.shared.MessageDirection
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags a message that is thrown (message end / intermediate throw event) but never caught anywhere in
 * the loaded fileset — silently lost cross-process communication that no single-model rule can detect,
 * since the catcher may live in another process file.
 *
 * Reported as WARN, not ERROR: a legitimate consumer outside the loaded fileset is possible, so the
 * rule can only warn. Cross-model — only meaningful with the whole related fileset loaded together,
 * so it is opt-in (see BpmnRules).
 */
class UncaughtMessageThrowRule : CrossModelValidationRule {

    override val id = "uncaught-message-throw"
    override val severity = Severity.WARN

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val thrownMessages = thrownMessages(context)
        val caughtMessageNames = caughtMessageNames(context)

        return thrownMessages
            .filterNot { (_, _, message) -> message.name in caughtMessageNames }
            .map { (model, node, message) ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = node.id,
                    processId = model.processId,
                    message = "Message '${message.name}' is thrown by '${node.id}' but has no catching event in the loaded models.",
                )
            }
    }

    private fun thrownMessages(
        context: CrossModelValidationContext,
    ): List<Triple<ProcessModel, FlowNodeDefinition, FlowNodeProperties.MessageEvent>> {
        return context.models.flatMap { model ->
            model.messageEvents(MessageDirection.THROW).map { (node, message) -> Triple(model, node, message) }
        }
    }

    private fun caughtMessageNames(context: CrossModelValidationContext): Set<String> {
        return context.models
            .flatMap { model -> model.messageEvents(MessageDirection.CATCH) }
            .map { (_, message) -> message.name }
            .toSet()
    }

    private fun ProcessModel.messageEvents(
        direction: MessageDirection,
    ): List<Pair<FlowNodeDefinition, FlowNodeProperties.MessageEvent>> {
        return flowNodes
            .mapNotNull { node -> node.messageEvent()?.let { node to it } }
            .filter { (_, message) -> message.direction == direction }
    }

    private fun FlowNodeDefinition.messageEvent(): FlowNodeProperties.MessageEvent? {
        return properties as? FlowNodeProperties.MessageEvent
    }
}
