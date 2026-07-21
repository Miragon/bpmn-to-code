package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Custom validation rules shared by **more than one** test class.
 *
 * A rule used by only a single test class stays a `private class` in that test - it lives here only
 * once a second suite needs it. Stateful probe rules (whose test inspects their fields) always stay local.
 */
object TestRules {

    const val CALL_ACTIVITY_TARGET_EXISTS = "call-activity-target-exists"

    /**
     * Cross-process rule: every call activity must reference a process that exists among the loaded models.
     *
     * Used by [CrossModelRuleTest] and [ValidationPhaseTest].
     */
    fun callActivityTargetExists(): CrossModelValidationRule {
        return CallActivityTargetExistsRule()
    }

    private class CallActivityTargetExistsRule : CrossModelValidationRule {
        override val id = CALL_ACTIVITY_TARGET_EXISTS
        override val severity = Severity.ERROR

        override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
            return context.models.flatMap { model ->
                model.callActivities
                    .filter { it.hasCalledElement() && context.resolveCalledModel(it) == null }
                    .map { callActivity ->
                        ValidationViolation(
                            ruleId = id,
                            severity = severity,
                            elementId = callActivity.id,
                            processId = model.processId,
                            message = "Call activity '${callActivity.id}' references unknown process '${callActivity.getValue()}'.",
                        )
                    }
            }
        }
    }
}
