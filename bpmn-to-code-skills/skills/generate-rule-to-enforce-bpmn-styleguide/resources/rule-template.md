# Rule template and domain cheat sheet

Rules compile against the `io.miragon.bpmn.domain` package, which ships inside `bpmn-to-code-testing`. Nothing from bpmn-to-code's sources is needed.

## Deterministic rule

```kotlin
// Generated from BPMN_STYLE_GUIDE.md by /generate-rule-to-enforce-bpmn-styleguide.
// Re-run the skill after editing the rule in the style guide.
package io.miragon.example.architecture.bpmn

import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

class ElementIdFormatRule : SingleModelValidationRule {

    override val id = "element-id-format"
    override val severity = Severity.ERROR

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        val pattern = Regex("^(serviceTask|userTask|startEvent|endEvent|gateway)_[a-z][a-zA-Z0-9]*$")
        val elementIds = model.allFlowNodes.mapNotNull { it.id }
        val nonMatchingIds = elementIds.filterNot { pattern.matches(it) }
        return nonMatchingIds.map { elementId ->
            violation(
                processId = model.processId,
                elementId = elementId,
                message = "Element id '$elementId' does not match the format type_descriptionInCamelCase.",
            )
        }
    }
}
```

For a `hybrid` rule, add one line below the header comment: `// Deterministic half only; the semantic half still needs a review.`

## What a rule can read

| Need | Expression |
|---|---|
| every flow node, including those in subprocesses | `model.allFlowNodes` (`flowNodes` is the root level only) |
| element id / display name | `node.id` (nullable), `node.displayName` |
| element kind | `node is FlowNodeDefinition.Gateway`, `.Event`, `.Activity.Task`, `.Activity.SubProcess`, `.Activity.CallActivity` |
| service tasks with their implementation (topic, job type, delegate expression) | `model.serviceTasks` |
| call activities with `inputMappings` / `outputMappings` | `model.callActivities` |
| timers with type and expression | `model.timers` |
| variables | `model.variables`, or `node.variables` per node |
| messages, signals, errors, escalations | `model.definitions.messages`, `.signals`, `.errors`, `.escalations` |
| sequence flows | `model.sequenceFlows` |
| the process | `model.processId`, `model.processName`, `model.isExecutable` |
| the engine under test | `context.engine` (`ZEEBE`, `CAMUNDA_7`, `OPERATON`) |

`violation(processId, message, elementId)` fills in the rule's `id` and `severity`. Leave out `elementId` for a finding about the whole process. Use `requireNotNull` instead of `!!`.

A rule that compares processes with each other implements `CrossModelValidationRule` and gets `context.models`, `context.findProcess(id)` and `context.resolveCalledModel(callActivity)`.

If a property is not listed here, let the compiler answer: write the expression, compile, and read the error. Or list the members with `javap -cp <path to bpmn-to-code-testing jar> io.miragon.bpmn.domain.ProcessModel`.

## Register the rule

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .withRules(*BpmnRules.all().toTypedArray(), ElementIdFormatRule())
    .validate()
    .assertNoViolations()
```

Docs: https://miragon.github.io/bpmn-to-code/validate/custom-rules
