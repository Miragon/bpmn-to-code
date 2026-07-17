@file:Suppress("DEPRECATION")

package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.validation.BpmnValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the backward-compatibility shims: rules written against the deprecated `BpmnValidationRule` and
 * `ValidationContext` names (now typealiases for [io.miragon.bpmn.domain.validation.SingleModelValidationRule]
 * and [io.miragon.bpmn.domain.validation.model.SingleModelValidationContext]) still compile and run.
 */
class DeprecatedRuleAliasTest {

    private class LegacyServiceTaskRule : BpmnValidationRule {
        override val id = "legacy-service-task"
        override val severity = Severity.WARN

        override fun validate(context: ValidationContext): List<ValidationViolation> {
            return context.model.serviceTasks.map { task ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = task.id,
                    processId = context.model.processId,
                    message = "seen '${task.id}'",
                )
            }
        }
    }

    @Test
    fun `a rule implementing the deprecated alias still runs through the validator`() {
        val result = BpmnValidator
            .fromClasspath("crossmodel/child-process.bpmn")
            .engine(ProcessEngine.CAMUNDA_7)
            .withRules(LegacyServiceTaskRule())
            .validate()
            .result()

        assertThat(result.violations.map { it.elementId }).contains("Task_DoWork")
    }
}
