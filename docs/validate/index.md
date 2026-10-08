# ✅ Validation

bpmn-to-code checks your BPMN models against a set of rules. The same rules run in three places:

| Where | Rules | Fails on | Can be tuned |
|-------|-------|----------|--------------|
| **Generation** — `generateBpmnModelApi`, `generateBpmnModelJson`, the Maven goals `generate-bpmn-api` and `generate-bpmn-json`, the [web app](/web/) | all [built-in rules](#built-in-rules) | any `ERROR` | no |
| **Validate task** — `validateBpmnModels` / `validate-bpmn` _(experimental)_ | the same rules | any `ERROR`, with `failOnWarning` also any `WARN` | `failOnWarning`, `disabledRules` |
| **[Testing library](/validate/testing)** — `bpmn-to-code-testing` | the built-in rules, opt-in rules and [your own](/validate/custom-rules) | whatever your test asserts | any rule can be selected or disabled |

Generation always validates first and writes nothing when a rule reports an `ERROR`: a service task without implementation, a timer without definition or a model built for another engine stops the run. Warnings are logged. There is no setting to relax this during generation — fix the model.

The validate task reports the same findings without generating anything, so you can run it earlier in the build or with stricter settings.

Two details:

- A process marked `isExecutable="false"` gets no Process API and is not validated when the API is generated. The JSON export and the validate task include it.
- Rules that compare processes with each other run only once every process is free of errors on its own.

## Built-in rules

| Rule ID | Severity | What it reports |
|---------|----------|-----------------|
| `missing-service-task-implementation` | ERROR | Service task with no implementation (Zeebe: no `zeebe:taskDefinition` type; Camunda 7 / Operaton: no topic, class or delegate expression) |
| `missing-message-name` | ERROR | Message event, send task or receive task whose message has no name |
| `missing-error-definition` | ERROR | Error event that references an error without a name or an error code. A catch-all error event that references no error passes |
| `missing-signal-name` | ERROR | Signal without a name |
| `missing-timer-definition` | ERROR | Timer event without a type (date, duration, cycle) or a value |
| `missing-called-element` | ERROR | Call activity that names no process to call |
| `missing-element-id` | ERROR | Flow node without an `id` · **mandatory** |
| `missing-process-id` | ERROR | Process without an `id` · **mandatory** |
| `empty-process` | WARN | Process without flow nodes |
| `collision-detection` | ERROR | Within one process: two element IDs that would be generated under the same name (`task_ship` and `task-ship`), the same ID declared in two scopes, or two mapping targets of one call activity that normalize to the same constant · **mandatory** |
| `reserved-element-name` | ERROR | Element ID that would be generated as a name the Process API reserves (`FlowNodes`, `All`, `Next`, `Instance`, runtime types, shared definitions, C# node members like `Id` or `Message`, …) · **mandatory** |
| `shared-definition-collision` | ERROR | Across all processes of the run: two different job types, messages, signals, errors, escalations or variables that normalize to the same constant name (`order.created` and `order-created`) · **mandatory** |
| `engine-mismatch` | ERROR / WARN | ERROR when the engine detected from the model's namespaces differs from the selected one; WARN when no engine can be detected |

**Mandatory** rules guarantee that the generated code compiles under unique names. `disabledRules` cannot turn them off; listing one logs a warning and keeps it active.

The testing library differs in two rules: it adds `unreferenced-root-element` (WARN — a message, signal, error or escalation that no element references) and has no `engine-mismatch`. Its opt-in rules are listed on the [Testing Module](/validate/testing#optional-rules-opt-in) page.

## Validate task

::: warning Experimental
`validateBpmnModels` and `validate-bpmn` are experimental and may change in a minor release.
:::

<!-- x-release-please-start-version -->
::: code-group

```kotlin [Gradle]
import io.miragon.bpmn.adapter.ValidateBpmnModelsTask
import io.miragon.bpmn.domain.shared.ProcessEngine

tasks.named("validateBpmnModels", ValidateBpmnModelsTask::class) {
    baseDir = "."
    filePattern = "src/main/resources/**/*.bpmn"
    processEngine = ProcessEngine.ZEEBE
    failOnWarning = true
    disabledRules = setOf("empty-process")
}
```

```xml [Maven]
<plugin>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-maven</artifactId>
    <version>6.2.0</version>
    <executions>
        <execution>
            <id>validate-bpmn</id>
            <phase>verify</phase>
            <goals><goal>validate-bpmn</goal></goals>
            <configuration>
                <filePattern>src/main/resources/**/*.bpmn</filePattern>
                <processEngine>ZEEBE</processEngine>
                <failOnWarning>true</failOnWarning>
                <disabledRules>
                    <disabledRule>empty-process</disabledRule>
                </disabledRules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

:::
<!-- x-release-please-end -->

`failOnWarning` (default `false`) and `disabledRules` (default empty) are optional. All parameters and their defaults are listed in [Configuration](/guide/configuration).

Run it:

```bash
# Gradle — the task is registered by the plugin
./gradlew validateBpmnModels

# Maven — through the phase the execution is bound to
mvn verify

# Maven — the execution alone
mvn bpmn-to-code:validate-bpmn@validate-bpmn
```

The Maven goal is bound to no phase by default, so the execution needs a `<phase>`. `@validate-bpmn` names the execution whose configuration applies; without it Maven runs the goal with its defaults.

Each finding is one line, followed by a summary when the build fails:

```
> Task :validateBpmnModels FAILED
[EXPERIMENTAL] The 'validateBpmnModels' task is experimental and may change in future releases.
[BPMN VALIDATION ERROR] bikeLeasing/serviceTask_sendContract: Service task has no implementation. Add a zeebe:taskDefinition with a type attribute. (rule: missing-service-task-implementation)
[BPMN VALIDATION ERROR] bikeLeasing/timer_signatureReminder: Timer event definition has no valid type (Date, Duration, or Cycle). (rule: missing-timer-definition)

BPMN validation failed: 2 error(s), 0 warning(s)
```

## Project-specific rules

The built-in rules cover what breaks generation or the engine. For your own conventions, write [custom rules](/validate/custom-rules) and run them with the [testing library](/validate/testing).
