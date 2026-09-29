# ⚙️ Configuration

All plugin parameters, available for both the Gradle and Maven plugins.

## Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `baseDir` | `String` | yes | — | Base directory for resolving relative paths |
| `filePattern` | `String` | yes | — | Glob pattern to locate BPMN files (e.g. `src/main/resources/**/*.bpmn`) |
| `outputFolderPath` | `String` | yes | — | Directory where generated code is written |
| `packagePath` | `String` | yes | — | Package name for generated classes (e.g. `com.example.process`). Use one package per generation run — the [shared definition files](/guide/generated-api#shared-definitions) of two runs in the same package overwrite each other |
| `outputLanguage` | `OutputLanguage` | yes | — | `KOTLIN`, `JAVA`, or `CSHARP` (experimental) |
| `processEngine` | `ProcessEngine` | yes | — | `ZEEBE`, `CAMUNDA_7`, or `OPERATON` |
| `enableVariants` | `Boolean` | no | `false` | Merge BPMN files sharing a `processId` into one API with `FlowVariants` (see [Model Merging](/guide/generated-api#model-merging)). When `false`, a `processId` defined in several files fails generation |

## Process Engines

| Engine | Value | Description |
|--------|-------|-------------|
| Camunda 8 / Zeebe | `ZEEBE` | Uses `zeebe:` namespace extensions |
| Camunda 7 | `CAMUNDA_7` | Uses `camunda:` namespace extensions |
| Operaton | `OPERATON` | Uses `operaton:` namespace (Operaton's own XML namespace) |

::: tip Operaton
Operaton is an open-source fork of Camunda 7. It uses the same patterns for I/O mappings and call activities, but with its own XML namespace (`http://operaton.org/schema/1.0/bpmn`). If your Operaton models still use `camunda:` namespace attributes, use `CAMUNDA_7` instead.
:::

## Output Languages

| Language | Value | Generated Output |
|----------|-------|-----------------|
| Kotlin | `KOTLIN` | `object` with nested objects; depends on `bpmn-to-code-runtime` |
| Java | `JAVA` | `class` with nested static classes; depends on `bpmn-to-code-runtime` |
| C# | `CSHARP` | `static class` with the same registries and `FlowNodes`; runtime types inlined, no dependency |

::: warning Experimental
C# support is experimental. It may change in any release and may be reworked or removed if it doesn't work out. [Feedback welcome](https://github.com/Miragon/bpmn-to-code/issues).
:::

::: info C# has no package dependency
The C# output carries the same API surface as Kotlin and Java, including the typed `FlowNodes` / `FlowVariants`
navigation. The runtime types its nodes need (`IFlowNode`, `SequenceFlow<T>`, `ElementId`, `VariableName`,
…) are emitted into every generated file as a nested `Runtime` class, so a `.cs` file drops into any project
and builds. See [C# specifics](/guide/generated-api#c-specifics).
:::

::: info
The Web app is the primary surface for C#. The Gradle and Maven plugins accept `CSHARP` as well — useful
in a polyglot monorepo where the JVM build also generates the constants for a sibling .NET worker — but a
pure .NET project has no JVM build to hook into.
:::

## Examples

::: code-group

```kotlin [Gradle (Kotlin DSL)]
tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/**/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "com.example.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}
```

```xml [Maven]
<configuration>
    <baseDir>${project.basedir}</baseDir>
    <filePattern>src/main/resources/*.bpmn</filePattern>
    <outputFolderPath>${project.basedir}/src/main/java</outputFolderPath>
    <packagePath>com.example.process</packagePath>
    <outputLanguage>KOTLIN</outputLanguage>
    <processEngine>ZEEBE</processEngine>
</configuration>
```

:::
