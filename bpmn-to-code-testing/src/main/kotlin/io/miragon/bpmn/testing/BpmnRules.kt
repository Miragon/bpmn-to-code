package io.miragon.bpmn.testing

import io.miragon.bpmn.domain.validation.BuiltInRules
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.ValidationRule
import io.miragon.bpmn.domain.validation.rules.CallActivityTargetExistsRule
import io.miragon.bpmn.domain.validation.rules.TimerCronSyntaxRule
import io.miragon.bpmn.domain.validation.rules.TimerIso8601SyntaxRule
import io.miragon.bpmn.domain.validation.rules.UncaughtMessageThrowRule
import io.miragon.bpmn.domain.validation.rules.UncaughtSignalThrowRule
import io.miragon.bpmn.domain.validation.rules.UnpublishedSignalCatchRule

/**
 * Provides access to all built-in BPMN validation rules.
 *
 * Each rule is exposed as a `@JvmField` constant for convenient use from both Kotlin and Java.
 * Use [all] to get a list of all built-in rules.
 */
object BpmnRules {

    /**
     * A model built for another engine than the selected one is read with the wrong vocabulary, so its job types,
     * messages and variables are missing from the generated API. Reported as WARN when no engine can be detected.
     */
    @JvmField
    val ENGINE_MISMATCH: SingleModelValidationRule = BuiltInRules.ENGINE_MISMATCH

    /**
     * Without a job type (Zeebe) or delegate expression (Camunda 7), the engine has no worker
     * to route the task to — the process hangs silently at runtime.
     */
    @JvmField
    val MISSING_SERVICE_TASK_IMPLEMENTATION: SingleModelValidationRule = BuiltInRules.MISSING_SERVICE_TASK_IMPLEMENTATION

    /**
     * Message correlation relies on the name as the subscription key; without it the engine
     * can't match incoming messages to catching events.
     */
    @JvmField
    val MISSING_MESSAGE_NAME: SingleModelValidationRule = BuiltInRules.MISSING_MESSAGE_NAME

    /**
     * Error boundary events need both name and code to be distinguishable; a missing code means
     * the engine can't differentiate this error from others and will swallow it.
     */
    @JvmField
    val MISSING_ERROR_DEFINITION: SingleModelValidationRule = BuiltInRules.MISSING_ERROR_DEFINITION

    /**
     * Signals are broadcast by name; without it, no catching event can subscribe
     * — broadcasts are silently lost.
     */
    @JvmField
    val MISSING_SIGNAL_NAME: SingleModelValidationRule = BuiltInRules.MISSING_SIGNAL_NAME

    /**
     * A message, signal, error or escalation that nothing references is usually left over from an
     * earlier version of the model. It still produces a constant in the generated API.
     */
    @JvmField
    val UNREFERENCED_ROOT_ELEMENT: SingleModelValidationRule = BuiltInRules.UNREFERENCED_ROOT_ELEMENT

    /**
     * Timers without a valid type (Date/Duration/Cycle) are a deployment-time error on most engines.
     */
    @JvmField
    val MISSING_TIMER_DEFINITION: SingleModelValidationRule = BuiltInRules.MISSING_TIMER_DEFINITION

    /**
     * Call activities resolve their subprocess by process ID at runtime; a missing reference
     * causes a deployment failure or runtime exception.
     */
    @JvmField
    val MISSING_CALLED_ELEMENT: SingleModelValidationRule = BuiltInRules.MISSING_CALLED_ELEMENT

    /**
     * The generated API derives its constant names from element IDs; elements without an ID
     * are silently omitted from the API.
     */
    @JvmField
    val MISSING_ELEMENT_ID: SingleModelValidationRule = BuiltInRules.MISSING_ELEMENT_ID

    /**
     * A process with no flow nodes produces an empty generated API, which is almost always
     * a modeling mistake. Reported as WARN since it is technically valid BPMN.
     */
    @JvmField
    val EMPTY_PROCESS: SingleModelValidationRule = BuiltInRules.EMPTY_PROCESS

    /**
     * The process ID is the deployment key and primary correlation handle;
     * a process without one can't be deployed.
     */
    @JvmField
    val MISSING_PROCESS_ID: SingleModelValidationRule = BuiltInRules.MISSING_PROCESS_ID

    /**
     * Distinct element ids that would be generated under one name within a Process API (`task_ship` and
     * `task-ship`) would clash there — this rule surfaces those conflicts before code generation.
     */
    @JvmField
    val COLLISION_DETECTION: SingleModelValidationRule = BuiltInRules.COLLISION_DETECTION

    /**
     * Element ids that would be generated as a name the Process API reserves for itself (`FlowNodes`, `Next`,
     * `Instance`, the runtime types, …) would shadow that part of the API, so the generated code could not compile.
     */
    @JvmField
    val RESERVED_ELEMENT_NAME: SingleModelValidationRule = BuiltInRules.RESERVED_ELEMENT_NAME

    /**
     * Job types, messages, signals, errors and escalations are generated once for all processes; two
     * of them normalizing to the same constant name — even in different processes — break the build.
     */
    @JvmField
    val SHARED_DEFINITION_COLLISION: CrossModelValidationRule = BuiltInRules.SHARED_DEFINITION_COLLISION

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
     * Opt-in: warns when a message is thrown (message end / intermediate throw event) but never caught
     * anywhere in the loaded fileset. Cross-model — only meaningful when the whole related fileset is
     * loaded together, so it is not part of [all]. Reported as WARN, since a consumer outside the
     * fileset is possible.
     */
    @JvmField
    val UNCAUGHT_MESSAGE_THROW: CrossModelValidationRule = UncaughtMessageThrowRule()

    /**
     * Opt-in: warns when a signal is thrown (signal end / intermediate throw event) but no catching
     * event subscribes to it anywhere in the loaded fileset. Cross-model — only meaningful when the
     * whole related fileset is loaded together, so it is not part of [all]. Reported as WARN, since
     * signals are broadcast and a subscriber outside the fileset is possible.
     */
    @JvmField
    val UNCAUGHT_SIGNAL_THROW: CrossModelValidationRule = UncaughtSignalThrowRule()

    /**
     * Opt-in: warns when a signal is caught (signal start / intermediate catch / boundary event) but no
     * throwing event publishes it anywhere in the loaded fileset — an orphaned subscriber, the mirror of
     * [UNCAUGHT_SIGNAL_THROW]. Cross-model — only meaningful when the whole related fileset is loaded
     * together, so it is not part of [all]. Reported as WARN, since signals are broadcast and a publisher
     * outside the fileset is possible.
     */
    @JvmField
    val UNPUBLISHED_SIGNAL_CATCH: CrossModelValidationRule = UnpublishedSignalCatchRule()

    /**
     * Returns all built-in BPMN validation rules that are enabled by default: the rules every generation runs, and
     * [UNREFERENCED_ROOT_ELEMENT]. Opt-in rules such as [TIMER_CRON_SYNTAX] and [TIMER_ISO8601_SYNTAX] are not
     * included — add them explicitly via [BpmnValidator.withRules].
     */
    @JvmStatic
    fun all(): List<ValidationRule> = BuiltInRules.defaults() + UNREFERENCED_ROOT_ELEMENT
}
