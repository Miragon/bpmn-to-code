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
 * Flags a signal that is thrown (signal end / intermediate throw event) but never caught anywhere in
 * the loaded fileset — a broadcast that goes nowhere, which no single-model rule can detect since the
 * subscriber may live in another process file.
 *
 * Reported as WARN, not ERROR: signals are broadcast, so a legitimate subscriber outside the loaded
 * fileset is possible and the rule can only warn. Cross-model — only meaningful with the whole related
 * fileset loaded together, so it is opt-in (see BpmnRules).
 */
class UncaughtSignalThrowRule : CrossModelValidationRule {

    override val id = "uncaught-signal-throw"
    override val severity = Severity.WARN

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val thrownSignals = thrownSignals(context)
        val caughtSignalNames = caughtSignalNames(context)

        return thrownSignals
            .filterNot { (_, _, signal) -> signal.name in caughtSignalNames }
            .map { (model, node, signal) ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = node.id,
                    processId = model.processId,
                    message = "Signal '${signal.name}' is thrown by '${node.id}' but has no catching event in the loaded models.",
                )
            }
    }

    private fun thrownSignals(
        context: CrossModelValidationContext,
    ): List<Triple<ProcessModel, FlowNodeDefinition, FlowNodeProperties.SignalEvent>> {
        return context.models.flatMap { model ->
            model.signalEvents(EventDirection.THROW).map { (node, signal) -> Triple(model, node, signal) }
        }
    }

    private fun caughtSignalNames(context: CrossModelValidationContext): Set<String> {
        return context.models
            .flatMap { model -> model.signalEvents(EventDirection.CATCH) }
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
