package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDirection
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
        val thrownNames = context.models
            .flatMap { it.signalUsages() }
            .filter { it.direction == EventDirection.THROW }
            .map { it.name }
            .toSet()

        return context.models.flatMap { model ->
            model.signalUsages()
                .filter { it.direction == EventDirection.CATCH && it.name !in thrownNames }
                .map { usage ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = usage.node.id,
                        processId = model.processId,
                        message = "Signal '${usage.name}' is caught by '${usage.node.id}' but has no throwing event in the loaded models.",
                    )
                }
        }
    }
}
