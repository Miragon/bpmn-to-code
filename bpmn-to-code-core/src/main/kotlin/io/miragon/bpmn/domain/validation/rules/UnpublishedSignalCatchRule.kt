package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowNodeProperties
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags a signal that is caught (signal start / intermediate catch / boundary event) but never thrown
 * anywhere in the loaded fileset — an orphaned subscriber waiting for a broadcast that no process
 * publishes. The mirror of [UncaughtSignalThrowRule]; no single-model rule can detect it since the
 * thrower may live in another process file.
 *
 * Reported as WARN, not ERROR: signals are broadcast, so a legitimate publisher outside the loaded
 * fileset is possible and the rule can only warn. Cross-model — only meaningful with the whole related
 * fileset loaded together, so it is opt-in (see BpmnRules).
 */
class UnpublishedSignalCatchRule : CrossModelValidationRule {

    override val id = "unpublished-signal-catch"
    override val severity = Severity.WARN

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val caughtSignals = caughtSignals(context)
        val thrownSignalNames = thrownSignalNames(context)

        return caughtSignals
            .filterNot { (_, _, signal) -> signal.name in thrownSignalNames }
            .map { (model, node, signal) ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = node.id,
                    processId = model.processId,
                    message = "Signal '${signal.name}' is caught by '${node.id}' but has no throwing event in the loaded models.",
                )
            }
    }

    private fun caughtSignals(
        context: CrossModelValidationContext,
    ): List<Triple<ProcessModel, FlowNodeDefinition, FlowNodeProperties.SignalEvent>> {
        return context.models.flatMap { model ->
            model.signalEvents(EventDirection.CATCH).map { (node, signal) -> Triple(model, node, signal) }
        }
    }

    private fun thrownSignalNames(context: CrossModelValidationContext): Set<String> {
        return context.models
            .flatMap { model -> model.signalEvents(EventDirection.THROW) }
            .map { (_, signal) -> signal.name }
            .toSet()
    }

    private fun ProcessModel.signalEvents(
        direction: EventDirection,
    ): List<Pair<FlowNodeDefinition, FlowNodeProperties.SignalEvent>> {
        return flowNodes
            .mapNotNull { node -> node.signalEvent()?.let { node to it } }
            .filter { (_, signal) -> signal.direction == direction }
    }

    private fun FlowNodeDefinition.signalEvent(): FlowNodeProperties.SignalEvent? {
        return properties as? FlowNodeProperties.SignalEvent
    }
}
