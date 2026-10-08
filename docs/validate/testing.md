# 🧪 Testing Module

::: warning Beta
This module is in beta. The API may change in a future release. [Leave feedback](https://github.com/Miragon/bpmn-to-code/issues) if you're using it.
:::

The `bpmn-to-code-testing` library lets you write architecture tests for your BPMN process models — the same way [ArchUnit](https://www.archunit.org/) lets you write architecture tests for Java code.

Add it to your test scope, write a test, and your CI will catch modeling issues before they reach production.

## Dependency

<!-- x-release-please-start-version -->
::: code-group

```kotlin [Gradle]
dependencies {
    testImplementation("io.miragon:bpmn-to-code-testing:6.2.0")
}
```

```xml [Maven]
<dependency>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-testing</artifactId>
    <version>6.2.0</version>
    <scope>test</scope>
</dependency>
```

:::
<!-- x-release-please-end -->

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

By default, `validate()` runs `BpmnRules.all()`. You can override the rule set:

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

Any rule can be disabled here, including the ones that are [mandatory](/validate/#built-in-rules) during generation: the testing module generates no code.

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
| `.assertViolation("rule-id", elementId, messageContains)` | Exactly one violation of the rule; `elementId` and `messageContains` are optional and narrow the match |
| `.assertViolationCount(n)` | Exactly `n` violations in total |
| `.assertNoErrors()` | No ERROR-severity violations |
| `.assertNoWarnings()` | No WARN-severity violations |
| `.result()` | Returns the raw `ValidationResult` for custom assertions |

```kotlin
val result = BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .validate()

result.assertNoErrors()
result.assertNoViolations("empty-process")
```

## Built-in Rules

`BpmnRules.all()` holds the rules described in the [rule table](/validate/#built-in-rules), each as a constant named after its ID (`missing-message-name` is `BpmnRules.MISSING_MESSAGE_NAME`). Two differences to generation and the validate task:

- `UNREFERENCED_ROOT_ELEMENT` (WARN) is included: a message, signal, error or escalation that no element references, usually left over from an earlier version of the model.
- `engine-mismatch` is not available.

## Optional Rules (opt-in)

These rules are not part of `BpmnRules.all()`. Enable them via `withRules(...)`.

| Rule | `BpmnRules` constant | Severity | Trigger |
|------|---------------------|----------|---------|
| Timer cycle is not valid cron | `TIMER_CRON_SYNTAX` | ERROR | A `timeCycle` timer whose value is not a valid cron expression |
| Timer value is not valid ISO-8601 | `TIMER_ISO8601_SYNTAX` | ERROR | A timer value that is not valid ISO-8601 for its type (Date → date/time, Duration → duration, Cycle → repeating interval) |
| Call activity target is missing | `CALL_ACTIVITY_TARGET_EXISTS` | ERROR | A call activity references a process that is not among the loaded models (a dangling call activity) |
| Thrown message has no catcher | `UNCAUGHT_MESSAGE_THROW` | WARN | A message is thrown (message end / intermediate throw event, send task) but no message start, intermediate catch or boundary event and no receive task catches it among the loaded models |
| Thrown signal has no subscriber | `UNCAUGHT_SIGNAL_THROW` | WARN | A signal is thrown (signal end / intermediate throw event) but no catching event subscribes to it among the loaded models |
| Caught signal is never thrown | `UNPUBLISHED_SIGNAL_CATCH` | WARN | A signal is caught (signal start / intermediate catch / boundary event) but no throwing event publishes it among the loaded models |

The last four compare processes with each other, so they only hold when all related files are loaded together — point `fromClasspath` / `fromDirectory` at the folder that holds them. That is why they are opt-in. The three WARN rules are warnings because the counterpart may live outside the loaded files. They run only when no single-model rule reports an `ERROR`.

```kotlin
BpmnValidator
    .fromClasspath("bpmn/")
    .engine(ProcessEngine.ZEEBE)
    .withRules(*BpmnRules.all().toTypedArray(), BpmnRules.TIMER_CRON_SYNTAX)
    .validate()
    .assertNoViolations()
```

Cron and ISO-8601 are mutually exclusive for `timeCycle` timers, so enable **one** of the two depending on your scheduling convention. Dynamic timer expressions (Camunda `${...}`, Zeebe FEEL `=...`) are skipped, since their value is only known at runtime.

## Custom Rules

For conventions of your own, implement a rule and pass it to `withRules(...)`. See [Custom Rules](/validate/custom-rules).
