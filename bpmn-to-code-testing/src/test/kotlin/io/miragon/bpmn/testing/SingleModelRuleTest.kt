package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Covers the single-model rule extension point: a custom [SingleModelValidationRule] can inspect the
 * parts of a single [io.miragon.bpmn.domain.BpmnModel]. Grouped by which part of the model the rule reaches.
 */
class SingleModelRuleTest {

    @Nested
    inner class CallActivityMappings {

        @Test
        fun `passes when the call activity declares the required input targets`() {
            BpmnValidator
                .fromClasspath("bpmn/c7-subscribe-newsletter.bpmn")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(RequireCallActivityInputsRule(setOf("childSubscriptionId", "childReasonCode")))
                .validate()
                .assertNoViolations()
        }

        @Test
        fun `flags a call activity missing a required input target`() {
            BpmnValidator
                .fromClasspath("bpmn/c7-subscribe-newsletter.bpmn")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(RequireCallActivityInputsRule(setOf("businessKey")))
                .validate()
                .assertViolation(
                    ruleId = "call-activity-required-inputs",
                    elementId = "CallActivity_AbortRegistration",
                    messageContains = "businessKey",
                )
        }

        @Test
        fun `passes when the call activity declares the required output target`() {
            BpmnValidator
                .fromClasspath("bpmn/c7-subscribe-newsletter.bpmn")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(RequireCallActivityOutputsRule(setOf("abortResult")))
                .validate()
                .assertNoViolations()
        }

        @Test
        fun `flags a call activity missing a required output target`() {
            BpmnValidator
                .fromClasspath("bpmn/c7-subscribe-newsletter.bpmn")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(RequireCallActivityOutputsRule(setOf("missingResult")))
                .validate()
                .assertViolation(
                    ruleId = "call-activity-required-outputs",
                    elementId = "CallActivity_AbortRegistration",
                    messageContains = "missingResult",
                )
        }
    }

    @Nested
    inner class OutputExpressions {

        @Test
        fun `can inspect output parameter value expressions`() {
            // only the disallowed expression is flagged - the allow-listed one passes
            BpmnValidator
                .fromClasspath("bpmn/output-mapping-process.bpmn")
                .engine(ProcessEngine.CAMUNDA_7)
                .withRules(OutputExpressionAllowListRule())
                .validate()
                .assertViolation(
                    ruleId = "output-expression-allow-list",
                    messageContains = "\${someBean.compute()}",
                )
        }
    }

    private class RequireCallActivityInputsRule(private val required: Set<String>) : SingleModelValidationRule {
        override val id = "call-activity-required-inputs"
        override val severity = Severity.ERROR

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            return context.model.callActivities.flatMap { callActivity ->
                val declaredTargets = callActivity.inputMappings.mapNotNull { it.target }.toSet()
                (required - declaredTargets).map { missing ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = callActivity.id,
                        processId = context.model.processId,
                        message = "Call activity '${callActivity.id}' must pass input variable '$missing' to the called process.",
                    )
                }
            }
        }
    }

    private class RequireCallActivityOutputsRule(private val required: Set<String>) : SingleModelValidationRule {
        override val id = "call-activity-required-outputs"
        override val severity = Severity.ERROR

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            return context.model.callActivities.flatMap { callActivity ->
                val declaredTargets = callActivity.outputMappings.mapNotNull { it.target }.toSet()
                (required - declaredTargets).map { missing ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = callActivity.id,
                        processId = context.model.processId,
                        message = "Call activity '${callActivity.id}' must return output variable '$missing' to the parent process.",
                    )
                }
            }
        }
    }

    /** Allows ${null}, ${true}, ${false} and ${execution.getVariable('...')} as output expressions. */
    private class OutputExpressionAllowListRule : SingleModelValidationRule {
        override val id = "output-expression-allow-list"
        override val severity = Severity.ERROR
        private val allowed = Regex("""\$\{(null|true|false|execution\.getVariable\('[^']+'\))}""")

        override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
            return context.model.flowNodes
                .flatMap { node -> node.variables.map { node to it } }
                .filter { (_, variable) -> variable.direction == VariableDirection.OUTPUT }
                .filter { (_, variable) ->
                    val expression = variable.valueExpression
                    expression != null && !allowed.matches(expression)
                }
                .map { (node, variable) ->
                    ValidationViolation(
                        ruleId = id,
                        severity = severity,
                        elementId = node.id,
                        processId = context.model.processId,
                        message = "Output expression '${variable.valueExpression}' is not allowed.",
                    )
                }
        }
    }
}
