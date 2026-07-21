# 🧪 Testing Module

::: warning Beta
This module is in beta. The API may change in a future release. [Leave feedback](https://github.com/Miragon/bpmn-to-code/issues) if you're using it.
:::

The `bpmn-to-code-testing` library lets you write architecture tests for your BPMN process models — the same way [ArchUnit](https://www.archunit.org/) lets you write architecture tests for Java code.

Add it to your test scope, write a test, and your CI will catch modeling issues before they reach production.

## Dependency

::: code-group

```kotlin [Gradle]
dependencies {
    testImplementation("io.miragon:bpmn-to-code-testing:3.0.0")
}
```

```xml [Maven]
<dependency>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-testing</artifactId>
    <version>3.0.0</version>
    <scope>test</scope>
</dependency>
```

:::

## Basic Usage

```kotlin
import io.miragon.bpmn.testing.BpmnValidator
import io.miragon.bpmn.domain.shared.ProcessEngine

@Test
fun `BPMN models should have no violations`() {
    BpmnValidator
        .fromClasspath("bpmn/")           // loads all .bpmn files from classpath:bpmn/
        .engine(ProcessEngine.ZEEBE)
        .validate()
        .assertNoViolations()
}
```

## Loading BPMN Files

| Method | What it does |
|--------|-------------|
| `BpmnValidator.fromClasspath("bpmn/")` | Loads all `.bpmn` files from the given classpath path |
| `BpmnValidator.fromDirectory(path)` | Loads all `.bpmn` files from a filesystem path (`java.nio.file.Path`) |

## Selecting Rules

By default, `validate()` runs all 10 built-in rules. You can override the rule set:

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.CAMUNDA_7)
    .withRules(BpmnRules.MISSING_SERVICE_TASK_IMPLEMENTATION, BpmnRules.MISSING_MESSAGE_NAME)
    .validate()
    .assertNoViolations()
```

Or pass a list:

```kotlin
.withRules(BpmnRules.all().filter { it.severity == Severity.ERROR })
```

## Disabling Rules

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .disableRules("empty-process")
    .validate()
    .assertNoViolations()
```

## Treating Warnings as Failures

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .failOnWarning()
    .validate()
    .assertNoViolations()
```

## Assertions

`validate()` returns a `BpmnValidationAssert` with AssertJ-style assertions:

| Assertion | What it checks |
|-----------|---------------|
| `.assertNoViolations()` | No violations at all (neither errors nor warnings) |
| `.assertNoViolations("rule-id")` | No violations for the given rule |
| `.assertHasViolations()` | At least one violation |
| `.assertNoErrors()` | No ERROR-severity violations |
| `.assertNoWarnings()` | No WARN-severity violations |
| `.result()` | Returns the raw `ValidationResult` for custom assertions |

```kotlin
val result = BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .validate()

result.assertNoErrors()
result.assertNoViolations("empty-process")  // custom: assert a specific rule produced no violations
```

## Built-in Rules Reference

| Rule | `BpmnRules` constant | Severity | Trigger |
|------|---------------------|----------|---------|
| Service task has no implementation | `MISSING_SERVICE_TASK_IMPLEMENTATION` | ERROR | Service task without jobType / delegate |
| Message event has no name | `MISSING_MESSAGE_NAME` | ERROR | Message start/catch/throw without a message name |
| Error event has no definition | `MISSING_ERROR_DEFINITION` | ERROR | Error boundary/end event without error definition |
| Signal event has no name | `MISSING_SIGNAL_NAME` | ERROR | Signal start/intermediate/end without signal name |
| Timer event has no definition | `MISSING_TIMER_DEFINITION` | ERROR | Timer event without type or value |
| Call activity has no calledElement | `MISSING_CALLED_ELEMENT` | ERROR | Call activity without `calledElement` attribute |
| Flow node has no ID | `MISSING_ELEMENT_ID` | ERROR | Any flow node missing an `id` attribute |
| Process has no ID | `MISSING_PROCESS_ID` | ERROR | Process element missing the `id` attribute |
| Process is empty | `EMPTY_PROCESS` | ERROR | Process with no flow nodes |
| Variable name collision | `COLLISION_DETECTION` | ERROR | Two different IDs normalize to the same constant name |

## Optional Rules (opt-in)

These rules are **not** part of `BpmnRules.all()` — they are off by default. Enable them explicitly via `withRules(...)` when you want to enforce the corresponding convention. They report as `ERROR` when enabled.

| Rule | `BpmnRules` constant | Severity | Trigger |
|------|---------------------|----------|---------|
| Timer cycle is not valid cron | `TIMER_CRON_SYNTAX` | ERROR | A `timeCycle` timer whose value is not a valid cron expression |
| Timer value is not valid ISO-8601 | `TIMER_ISO8601_SYNTAX` | ERROR | A timer value that is not valid ISO-8601 for its type (Date → date/time, Duration → duration, Cycle → repeating interval) |
| Call activity target is missing | `CALL_ACTIVITY_TARGET_EXISTS` | ERROR | A call activity references a process that is not among the loaded models (a dangling call activity) |

`CALL_ACTIVITY_TARGET_EXISTS` is a [cross-model rule](#cross-process-multi-model-rules): it only holds when the **whole** related fileset is loaded together, which is exactly why it is opt-in rather than part of `all()` — see the tip on loading the whole set below.

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .withRules(*BpmnRules.all().toTypedArray(), BpmnRules.TIMER_CRON_SYNTAX)
    .validate()
    .assertNoViolations()
```

Cron and ISO-8601 are mutually exclusive for `timeCycle` timers, so enable **one** of the two depending on your scheduling convention. Dynamic timer expressions (Camunda `${...}`, Zeebe FEEL `=...`) are skipped, since their value is only known at runtime.

## Writing Custom Rules

Implement the `SingleModelValidationRule` interface to add project-specific checks. Each rule is
invoked once per process model and sees a single model through its `SingleModelValidationContext`:

::: warning Renamed
`BpmnValidationRule` → `SingleModelValidationRule` and `ValidationContext` → `SingleModelValidationContext`
(renamed in 4.1.0 to contrast with the new [`CrossModelValidationRule`](#cross-process-multi-model-rules) /
`CrossModelValidationContext`). The old names were kept as deprecated typealiases in 4.1.x and **removed in
5.0.0** — use the new names. See the [v5 migration guide](/changelog/v5).
:::

```kotlin
class RequireElementPrefixRule : SingleModelValidationRule {

    override val id = "require-element-prefix"
    override val severity = Severity.WARN

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        return context.model.flowNodes
            .filter { !it.id.contains("_") }
            .map { node ->
                ValidationViolation(
                    ruleId = id,
                    severity = severity,
                    elementId = node.id,
                    processId = context.model.processId,
                    message = "Element '${node.id}' has no type prefix (e.g. 'Activity_', 'Task_').",
                )
            }
    }
}
```

Use it in tests:

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .withRules(*BpmnRules.all().toTypedArray(), RequireElementPrefixRule())
    .validate()
    .assertNoViolations()
```

## Asserting Call-Activity Variable Mappings

Call activities expose their input/output variable mappings via `CallActivityDefinition.mappings`,
with `inputMappings` / `outputMappings` helpers. Each `CallActivityMapping` keeps **both** sides of
the mapping — the `source` (or `sourceExpression`) and the `target` — so you can assert on the name a
variable gets inside the called process. For example, requiring every call activity to pass a
`businessKey` and `correlationKey`:

```kotlin
class RequireCallActivityInputsRule(private val required: Set<String>) : SingleModelValidationRule {

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
                    message = "Call activity '${callActivity.id}' must pass '$missing' to the called process.",
                )
            }
        }
    }
}
```

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.CAMUNDA_7)
    .withRules(RequireCallActivityInputsRule(setOf("businessKey", "correlationKey")))
    .validate()
    .assertNoViolations()
```

::: tip Engine differences
For **Camunda 7 / Operaton** (`camunda:in` / `camunda:out`), `source` is a plain variable name and
`sourceExpression` holds a `${...}` expression. For **Zeebe** (`zeebe:input` / `zeebe:output`),
`source` is a FEEL expression (e.g. `=orderId`) and `sourceExpression` is always `null`. The `target`
— the name inside the called process — is populated the same way for all engines.

The "pass all variables" mode (`variables="all"` / `propagateAll{Parent,Child}Variables`) is exposed
separately via `propagateAllInputVariables` / `propagateAllOutputVariables`, which are tri-state:

| Value | Meaning |
|-------|---------|
| `true` | The model explicitly enables pass-all (Camunda 7 / Operaton `variables="all"`; Zeebe `propagateAll…="true"`). |
| `false` | The model explicitly disables it. **Zeebe only** — Camunda 7 / Operaton have no attribute to express this, so they never report `false`. |
| `null` | Not declared in the model. |

For a rule that should behave the same across engines, test `!= true` to mean "does not pass all
variables" (this treats Camunda's "not declared" and Zeebe's explicit `false` alike).
:::

::: tip Also available in the generated API
The same mappings are surfaced in the [generated Process API](/guide/generated-api#call-activity-variable-mappings) under `CallActivities.<CallActivity>.Inputs` / `.Outputs` as `InputOutputMapping` constants, so production code can reference them type-safely too — not just validation rules.
:::

## Cross-Process (Multi-Model) Rules

A [`SingleModelValidationRule`](#writing-custom-rules) sees one process at a time — it can never reason
about the relationship *between* two processes. For checks that span files — for example whether a call
activity's called process actually exists among your models — implement `CrossModelValidationRule`
instead. It is invoked **once** with a `CrossModelValidationContext` that carries *all* loaded models
and can resolve cross-process references:

- `context.models` — every loaded process model.
- `context.findProcess(processId)` — look up a model by its process id.
- `context.resolveCalledModel(callActivity)` — resolve a call activity's called element to the model of
  the called process, or `null` if it has none or references an unknown process.

::: tip Already built-in
The dangling-call-activity check below **ships built-in** as the opt-in rule
`BpmnRules.CALL_ACTIVITY_TARGET_EXISTS` (see [Optional Rules](#optional-rules-opt-in)) — you don't have to
write it yourself. It is reproduced here as a template for your own cross-model rules.
:::

The example flags any call activity that references a process not present among the loaded models
(a dangling call activity — a runtime failure that no single-model rule can catch, because the parent
and called process have different ids and never appear together):

```kotlin
class CallActivityTargetExistsRule : CrossModelValidationRule {

    override val id = "call-activity-target-exists"
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
```

Cross-model rules go through the same `.withRules(...)` flow and can be mixed freely with single-model
rules in one run — the validator routes each to the right execution phase. Here the built-in
`CALL_ACTIVITY_TARGET_EXISTS` is added on top of the default rule set (swap it for your own rule to use
a custom one):

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.CAMUNDA_7)
    .withRules(*BpmnRules.all().toTypedArray(), BpmnRules.CALL_ACTIVITY_TARGET_EXISTS)
    .validate()
    .assertNoViolations()
```

::: tip Load the whole set
Cross-process rules only pay off when **all** related files are loaded together — point
`fromClasspath` / `fromDirectory` at the folder holding both the parent and the called processes so
`resolveCalledModel` can find them. Cross-model rules run after models are merged; if a **pre-merge**
single-model rule reports an `ERROR`, validation stops before the merge and the cross-model phase never
runs (post-merge rules like `COLLISION_DETECTION` do not short-circuit it).
:::

Other cross-process checks you can write with the same building blocks: input-coverage ("does every
caller pass the variables the called process reads?"), output-consumption, message/signal correlation
across processes, and process-id uniqueness across the whole fileset.

::: tip SingleModelValidationContext
`context.model` gives you the full `BpmnModel` — flow nodes, service tasks, call activities, messages, signals, errors, timers, and variables. `context.engine` tells you which engine was selected, so you can write engine-specific rules.
:::
