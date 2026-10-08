# 🧩 Custom Rules

::: warning Beta
Custom rules are part of the [testing module](/validate/testing), which is in beta.
:::

A custom rule checks a convention of your own — a naming scheme, a variable every call activity must pass — and runs in your tests next to the built-in rules. There are two kinds:

| Interface | Invoked | Sees |
|-----------|---------|------|
| `SingleModelValidationRule` | once per process | one `ProcessModel` and the selected engine |
| `CrossModelValidationRule` | once per run | all loaded models, with lookups between them |

Rules are written against the `io.miragon.bpmn.domain` package of `bpmn-to-code-core`, which is bundled in the `bpmn-to-code-testing` jar. That package is a supported API for this purpose only: writing validation rules.

## A single-model rule

A rule has an `id`, a `severity` and a `validate` function that returns its findings. This one requires every element ID to carry a type prefix such as `serviceTask_`:

```kotlin
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class RequireElementPrefixRule : SingleModelValidationRule {

    override val id = "require-element-prefix"
    override val severity = Severity.WARN

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        val elementIds = model.allFlowNodes.mapNotNull { it.id }
        val idsWithoutPrefix = elementIds.filterNot { it.contains("_") }
        return idsWithoutPrefix.map { elementId ->
            violation(
                processId = model.processId,
                elementId = elementId,
                message = "Element '$elementId' has no type prefix such as 'serviceTask_'.",
            )
        }
    }
}
```

- `context.model` is the `ProcessModel` of one BPMN file; `context.engine` is the engine passed to `BpmnValidator.engine(...)`.
- `allFlowNodes` holds every element including those inside subprocesses; `flowNodes` holds the root level only.
- An element's `id` is nullable, because a model may omit it (`missing-element-id` reports that).
- Messages, signals, errors and escalations are under `model.definitions`; `model.serviceTasks`, `model.callActivities`, `model.timers` and `model.variables` are ready-made views over all flow nodes.
- `violation(...)` fills in the rule's `id` and `severity`. Leave out `elementId` for a finding about the whole process.

Add the rule to a test:

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .withRules(*BpmnRules.all().toTypedArray(), RequireElementPrefixRule())
    .validate()
    .assertNoViolations()
```

## Example: required call-activity inputs

`model.callActivities` exposes each call activity's variable mappings as `inputMappings` and `outputMappings`. A mapping keeps both sides — `source` (or `sourceExpression`) and `target`, the name inside the called process. This rule requires every call activity to pass a set of variables:

```kotlin
class RequireCallActivityInputsRule(private val requiredInputs: Set<String>) : SingleModelValidationRule {

    override val id = "call-activity-required-inputs"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        return model.callActivities.flatMap { callActivity ->
            val passedInputs = callActivity.inputMappings.mapNotNull { it.target }.toSet()
            val missingInputs = requiredInputs - passedInputs
            missingInputs.map { missingInput ->
                violation(
                    processId = model.processId,
                    elementId = callActivity.id,
                    message = "Call activity must pass '$missingInput' to the called process.",
                )
            }
        }
    }
}
```

```kotlin
.withRules(RequireCallActivityInputsRule(setOf("applicationId")))
```

Engine differences in a mapping:

| | Camunda 7 / Operaton (`camunda:in` / `camunda:out`) | Zeebe (`zeebe:input` / `zeebe:output`) |
|---|---|---|
| `source` | plain variable name | FEEL expression such as `=applicationId` |
| `sourceExpression` | a `${...}` expression | always `null` |
| `target` | name inside the called process | name inside the called process |
| `propagateAllInputVariables` / `propagateAllOutputVariables` | `true` for `variables="all"`, otherwise `null` | `true` or `false` as modelled, `null` when not declared |

To treat "not declared" and an explicit `false` alike, test `!= true`.

The same mappings are in the [generated API](/guide/generated-api#call-activity-variable-mappings) as `FlowNodes.<CallActivity>.Inputs` / `.Outputs`.

## A cross-model rule

A single-model rule cannot see the relationship between two processes. `CrossModelValidationRule` is invoked once with a `CrossModelValidationContext`:

- `context.models` — every loaded process model.
- `context.findProcess(processId)` — the model with that process ID, or `null`. Where several files declare it, the first one.
- `context.resolveCalledModel(callActivity)` — the model a call activity calls, or `null` if it names no process or an unknown one.

This rule reports call activities whose called process is not among the loaded models. It ships built-in as the opt-in `BpmnRules.CALL_ACTIVITY_TARGET_EXISTS` and is shown here as a template:

```kotlin
import io.miragon.bpmn.domain.validation.CrossModelValidationRule
import io.miragon.bpmn.domain.validation.model.CrossModelValidationContext
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class CallActivityTargetExistsRule : CrossModelValidationRule {

    override val id = "call-activity-target-exists"
    override val severity = Severity.ERROR

    override fun validate(context: CrossModelValidationContext): List<ValidationViolation> {
        return context.models.flatMap { model ->
            val danglingCallActivities = model.callActivities.filter {
                it.hasCalledElement() && context.resolveCalledModel(it) == null
            }
            danglingCallActivities.map { callActivity ->
                violation(
                    processId = model.processId,
                    elementId = callActivity.id,
                    message = "Call activity references unknown process '${callActivity.calledElement}'.",
                )
            }
        }
    }
}
```

Cross-model rules go through the same `withRules(...)` and mix freely with single-model rules. Two things to know:

- **Load the whole set.** Point `fromClasspath` / `fromDirectory` at the folder that holds the calling and the called processes, otherwise `resolveCalledModel` cannot find them.
- **Single-model rules run first.** If one of them reports an `ERROR`, validation stops there and no cross-model rule runs.
