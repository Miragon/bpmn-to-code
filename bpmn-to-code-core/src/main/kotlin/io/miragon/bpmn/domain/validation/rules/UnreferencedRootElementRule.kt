package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Flags `bpmn:Definitions` root elements — messages, signals, errors, escalations — that no flow node
 * references.
 *
 * Modelers leave these behind: deleting the event that used a message does not delete the message
 * itself. The declaration is still valid BPMN, so this is a warning rather than an error, but it does
 * have consequences. The generated Process API gets a constant nothing correlates to, and the JSON
 * registry gets an entry no `…Ref` points at — both of which read as "this process handles that
 * message" when it does not.
 */
class UnreferencedRootElementRule : SingleModelValidationRule {

    override val id = "unreferenced-root-element"
    override val severity = Severity.WARN

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        val referenced = model.referencedDefinitionIds()
        return model.definitions.run {
            unreferenced(messages, referenced, "Message") +
                unreferenced(signals, referenced, "Signal") +
                unreferenced(errors, referenced, "Error") +
                unreferenced(escalations, referenced, "Escalation")
        }.map { (kind, element) -> violation(model, kind, element) }
    }

    private fun <T : RootElementDefinition> unreferenced(
        elements: List<T>,
        referenced: Set<String>,
        kind: String,
    ): List<Pair<String, T>> = elements.filterNot { it.id in referenced }.map { kind to it }

    private fun violation(
        model: ProcessModel,
        kind: String,
        element: RootElementDefinition,
    ): ValidationViolation = ValidationViolation(
        ruleId = id,
        severity = severity,
        elementId = element.id,
        processId = model.processId,
        message = "$kind '${element.id}' is declared but no element references it. " +
            "It still produces a constant in the generated API — remove it from the BPMN file " +
            "if it is left over from an earlier version of the model.",
    )
}
