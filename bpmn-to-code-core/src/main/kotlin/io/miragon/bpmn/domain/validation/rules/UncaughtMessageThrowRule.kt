package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags a message that is thrown (message end / intermediate throw event, send task) but never caught
 * anywhere in the loaded fileset — silently lost cross-process communication that no single-model rule can
 * detect, since the catcher may live in another process file.
 *
 * Reported as WARN, not ERROR: a legitimate consumer outside the loaded fileset is possible, so the
 * rule can only warn. Cross-model — only meaningful with the whole related fileset loaded together,
 * so it is opt-in (see BpmnRules).
 */
class UncaughtMessageThrowRule : CrossModelValidationRule {

    override val id = "uncaught-message-throw"
    override val severity = Severity.WARN

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        val caughtNames = context.models
            .flatMap { it.messageUsages() }
            .filter { it.direction == EventDirection.CATCH }
            .map { it.name }
            .toSet()

        return context.models.flatMap { model ->
            model.messageUsages()
                .filter { it.direction == EventDirection.THROW && it.name !in caughtNames }
                .map { usage ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = usage.node.id,
                        processId = model.processId,
                        message = "Message '${usage.name}' is thrown by '${usage.node.id}' but has no catching event in the loaded models.",
                    )
                }
        }
    }
}
