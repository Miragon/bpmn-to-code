package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDirection
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
        val caughtNames = context.models
            .flatMap { it.signalUsages() }
            .filter { it.direction == EventDirection.CATCH }
            .map { it.name }
            .toSet()

        return context.models.flatMap { model ->
            model.signalUsages()
                .filter { it.direction == EventDirection.THROW && it.name !in caughtNames }
                .map { usage ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = usage.node.id,
                        processId = model.processId,
                        message = "Signal '${usage.name}' is thrown by '${usage.node.id}' but has no catching event in the loaded models.",
                    )
                }
        }
    }
}
