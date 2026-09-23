# ⚙️ Configuration

All plugin parameters, available for both the Gradle and Maven plugins.

## Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `baseDir` | `String` | yes | — | Base directory for resolving relative paths |
| `filePattern` | `String` | yes | — | Glob pattern to locate BPMN files (e.g. `src/main/resources/**/*.bpmn`) |
| `outputFolderPath` | `String` | yes | — | Directory where generated code is written |
| `packagePath` | `String` | yes | — | Package name for generated classes (e.g. `com.example.process`) |
| `outputLanguage` | `OutputLanguage` | yes | — | `KOTLIN`, `JAVA`, or `CSHARP` (beta) |
| `processEngine` | `ProcessEngine` | yes | — | `ZEEBE`, `CAMUNDA_7`, or `OPERATON` |

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
| Kotlin | `KOTLIN` | `object` with `const val` properties |
| Java | `JAVA` | `class` with `public static final` fields |
| C# | `CSHARP` | `static class` with `const string` fields — **beta**, constants only |

::: warning C# is beta
The C# target emits the **constants** sections only: process id, engine, element ids, call activities,
messages, service tasks, timers, errors, escalations, signals and variables.

The typed navigation DSL (`Flow` / `Variants`) is **not** generated. Every node of it derives from
`bpmn-to-code-runtime`, which is a JVM artifact; until a C# counterpart exists, a partial navigation API
could not compile. In exchange, the generated `.cs` file has **no dependencies at all** — drop it into a
project and it builds.

Direction of a process variable, which the JVM APIs carry in a wrapper type, is documented on each
constant instead so it still shows up in IntelliSense.
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
