package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class MissingServiceTaskImplementationRule : SingleModelValidationRule {

    override val id = "missing-service-task-implementation"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.serviceTasks
            .filter { !it.hasImplementation() }
            .map { task ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = task.id,
                    processId = context.model.processId,
                    message = "Service task has no implementation. ${engineHint(context.engine)}",
                )
            }
    }

    private fun engineHint(engine: ProcessEngine): String = when (engine) {
        ProcessEngine.CAMUNDA_7 -> "Set camunda:topic, camunda:class, or camunda:delegateExpression."
        ProcessEngine.OPERATON -> "Set operaton:topic, operaton:class, or operaton:delegateExpression."
        ProcessEngine.ZEEBE -> "Add a zeebe:taskDefinition with a type attribute."
    }
}
