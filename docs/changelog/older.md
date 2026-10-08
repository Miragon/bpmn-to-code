# Upgrading from 2.x–5.x

The breaking changes of the majors before 6.0, newest first. Coming from an old version, work through the sections from your version upwards, then continue with the [v6 Migration Guide](./v6). Much of what 2.0 introduced was reshaped again in 6.0, so regenerate once on the target version and fix what the compiler reports rather than migrating step by step.

## 5.0 — deprecated rule aliases removed

Affects you only if you wrote custom validation rules. The names deprecated in 4.1.0 are gone:

| Before | After |
|--------|-------|
| `io.miragon.bpmn.domain.validation.BpmnValidationRule` | `io.miragon.bpmn.domain.validation.SingleModelValidationRule` |
| `io.miragon.bpmn.domain.validation.model.ValidationContext` | `io.miragon.bpmn.domain.validation.model.SingleModelValidationContext` |

```kotlin
// Before
class RequireElementPrefixRule : BpmnValidationRule {
    override fun validate(context: ValidationContext): List<ValidationViolation> { /* ... */ }
}

// After
class RequireElementPrefixRule : SingleModelValidationRule {
    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> { /* ... */ }
}
```

The rename made room for [cross-model rules](/validate/custom-rules#a-cross-model-rule). Built-in rules, `BpmnValidator` and the assertions are unchanged.

## 4.0 — `io.github.emaarco` runtime types removed

The `io.miragon` runtime jar of 3.x still shipped the runtime types under `io.github.emaarco.bpmn.runtime.*` as deprecated aliases. 4.0 removes them.

Affects you only if your generated code still imports `io.github.emaarco.bpmn.runtime.*`:

1. Regenerate the Process API so its imports point at `io.miragon.bpmn.runtime.*`.
2. Change your own imports of `io.github.emaarco.bpmn.runtime.*` to `io.miragon.bpmn.runtime.*`. The types are otherwise identical.

## 3.0 — `io.miragon` namespace

Only coordinates and package names changed:

| | Before | After |
|---|--------|-------|
| Maven group | `io.github.emaarco` | `io.miragon` |
| Gradle plugin ID | `io.github.emaarco.bpmn-to-code-gradle` | `io.miragon.bpmn-to-code-gradle` |
| Runtime imports in generated code | `io.github.emaarco.bpmn.runtime.*` | `io.miragon.bpmn.runtime.*` |

1. Switch the plugin ID (Gradle) or the plugin's `groupId` (Maven), and the `groupId` of the runtime and testing dependencies if you declare them.
2. Regenerate the Process API and update your own imports in the same step. Do not mix both packages within a module: `io.github.emaarco.bpmn.runtime.ProcessId` and `io.miragon.bpmn.runtime.ProcessId` are distinct types.

The last `io.github.emaarco` release, 3.0.0, is published from the former [`emaarco/bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) repository.

## 2.0 — typed constants and the runtime artifact

| Change | Migration |
|--------|-----------|
| `TaskTypes` renamed to `ServiceTasks` | Replace `.TaskTypes.` with `.ServiceTasks.` |
| `BpmnTimer`, `BpmnError` and the other shared types moved out of the generated code into the `bpmn-to-code-runtime` artifact | Import them from the runtime package. Gradle adds the dependency itself; Maven users declare `bpmn-to-code-runtime` once |
| Variables are nested per element: `Variables.SUBSCRIPTION_ID` became `Variables.<Element>.SUBSCRIPTION_ID` | Look up the element in the regenerated API |
| A variable's direction is its type: `VariableName.Input`, `.Output` or `.InOut` | Nothing, unless you want APIs that accept one direction only |
| Constants are typed wrappers (`ProcessId`, `ElementId`, `MessageName`, `SignalName`, `VariableName`) instead of `String`. `ServiceTasks.*` stays a `String` constant for annotations | Accept the wrapper in your own signatures, or pass `.value` where an API requires a `String`. `toString()` returns the raw value, so logging and string templates need no change |
| The extension property `additionalVariables` is no longer read | Split it into `additionalInputVariables` and `additionalOutputVariables` in your BPMN files |

Wrapped constants cannot be annotation arguments. Where you used a message name in an annotation, keep a `const val` of your own next to it.
