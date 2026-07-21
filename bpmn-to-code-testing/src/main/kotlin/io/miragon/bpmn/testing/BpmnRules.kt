package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.rules.CallActivityTargetExistsRule
import io.miragon.bpmn.domain.validation.rules.CollisionDetectionRule
import io.miragon.bpmn.domain.validation.rules.EmptyProcessRule
import io.miragon.bpmn.domain.validation.rules.MissingCalledElementRule
import io.miragon.bpmn.domain.validation.rules.MissingElementIdRule
import io.miragon.bpmn.domain.validation.rules.MissingErrorDefinitionRule
import io.miragon.bpmn.domain.validation.rules.MissingMessageNameRule
import io.miragon.bpmn.domain.validation.rules.MissingProcessIdRule
import io.miragon.bpmn.domain.validation.rules.MissingServiceTaskImplementationRule
import io.miragon.bpmn.domain.validation.rules.MissingSignalNameRule
import io.miragon.bpmn.domain.validation.rules.MissingTimerDefinitionRule
import io.miragon.bpmn.domain.validation.rules.TimerCronSyntaxRule
import io.miragon.bpmn.domain.validation.rules.TimerIso8601SyntaxRule

/**
 * Provides access to all built-in BPMN validation rules.
 *
 * Each rule is exposed as a `@JvmField` constant for convenient use from both Kotlin and Java.
 * Use [all] to get a list of all built-in rules.
 */
object BpmnRules {

    /**
     * Without a job type (Zeebe) or delegate expression (Camunda 7), the engine has no worker
     * to route the task to — the process hangs silently at runtime.
     */
    @JvmField
    val MISSING_SERVICE_TASK_IMPLEMENTATION: SingleModelValidationRule = MissingServiceTaskImplementationRule()

    /**
     * Message correlation relies on the name as the subscription key; without it the engine
     * can't match incoming messages to catching events.
     */
    @JvmField
    val MISSING_MESSAGE_NAME: SingleModelValidationRule = MissingMessageNameRule()

    /**
     * Error boundary events need both name and code to be distinguishable; a missing code means
     * the engine can't differentiate this error from others and will swallow it.
     */
    @JvmField
    val MISSING_ERROR_DEFINITION: SingleModelValidationRule = MissingErrorDefinitionRule()

    /**
     * Signals are broadcast by name; without it, no catching event can subscribe
     * — broadcasts are silently lost.
     */
    @JvmField
    val MISSING_SIGNAL_NAME: SingleModelValidationRule = MissingSignalNameRule()

    /**
     * Timers without a valid type (Date/Duration/Cycle) are a deployment-time error on most engines.
     */
    @JvmField
    val MISSING_TIMER_DEFINITION: SingleModelValidationRule = MissingTimerDefinitionRule()

    /**
     * Call activities resolve their subprocess by process ID at runtime; a missing reference
     * causes a deployment failure or runtime exception.
     */
    @JvmField
    val MISSING_CALLED_ELEMENT: SingleModelValidationRule = MissingCalledElementRule()

    /**
     * The generated API derives its constant names from element IDs; elements without an ID
     * are silently omitted from the API.
     */
    @JvmField
    val MISSING_ELEMENT_ID: SingleModelValidationRule = MissingElementIdRule()

    /**
     * A process with no flow nodes produces an empty generated API, which is almost always
     * a modeling mistake. Reported as WARN since it is technically valid BPMN.
     */
    @JvmField
    val EMPTY_PROCESS: SingleModelValidationRule = EmptyProcessRule()

    /**
     * The process ID is the deployment key and primary correlation handle;
     * a process without one can't be deployed.
     */
    @JvmField
    val MISSING_PROCESS_ID: SingleModelValidationRule = MissingProcessIdRule()

    /**
     * When multiple BPMN files declare the same process ID (e.g., engine variants), conflicting
     * element definitions are silently overwritten during merge — this rule surfaces those
     * conflicts before code generation.
     */
    @JvmField
    val COLLISION_DETECTION: SingleModelValidationRule = CollisionDetectionRule()

    // --- Optional rules (opt-in) ------------------------------------------------------------------
    // Not part of [all]. Enable explicitly via BpmnValidator.withRules(...) to enforce a timer-format
    // convention. Cron and ISO are mutually exclusive for timeCycle timers, so enable one of the two.

    /**
     * Opt-in: enforces that `timeCycle` timers use a valid cron expression. Not part of [all].
     */
    @JvmField
    val TIMER_CRON_SYNTAX: SingleModelValidationRule = TimerCronSyntaxRule()

    /**
     * Opt-in: enforces that timer values are valid ISO-8601 for their type
     * (Date -> date/time, Duration -> duration, Cycle -> repeating interval). Not part of [all].
     */
    @JvmField
    val TIMER_ISO8601_SYNTAX: SingleModelValidationRule = TimerIso8601SyntaxRule()

    /**
     * Opt-in: flags call activities whose called element references a process that is not among the
     * loaded models (a dangling call activity). Cross-model — only meaningful when the whole related
     * fileset is loaded together, so it is not part of [all].
     */
    @JvmField
    val CALL_ACTIVITY_TARGET_EXISTS: CrossModelValidationRule = CallActivityTargetExistsRule()

    /**
     * Returns all built-in BPMN validation rules that are enabled by default.
     * Opt-in rules such as [TIMER_CRON_SYNTAX] and [TIMER_ISO8601_SYNTAX] are not included — add them
     * explicitly via [BpmnValidator.withRules].
     */
    @JvmStatic
    fun all(): List<SingleModelValidationRule> {
        return listOf(
            MISSING_SERVICE_TASK_IMPLEMENTATION,
            MISSING_MESSAGE_NAME,
            MISSING_ERROR_DEFINITION,
            MISSING_SIGNAL_NAME,
            MISSING_TIMER_DEFINITION,
            MISSING_CALLED_ELEMENT,
            MISSING_ELEMENT_ID,
            EMPTY_PROCESS,
            MISSING_PROCESS_ID,
            COLLISION_DETECTION,
        )
    }
}
