# 🚀 Gradle Setup

The Gradle plugin generates the Process API from your BPMN models as part of the build. It is published on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle).

**Requirements:** the build runs on JDK 21 or newer. The plugin and `bpmn-to-code-runtime` are compiled for Java 21.

## 1. Apply the plugin

<!-- x-release-please-start-version -->
::: code-group

```kotlin [build.gradle.kts]
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "6.2.0"
}
```

```groovy [build.gradle]
plugins {
    id 'io.miragon.bpmn-to-code-gradle' version '6.2.0'
}
```

:::
<!-- x-release-please-end -->

The plugin is resolved from the Gradle Plugin Portal, which is Gradle's default plugin repository. If your `settings.gradle.kts` declares `pluginManagement.repositories`, make sure `gradlePluginPortal()` is among them.

## 2. Configure the generation task

::: code-group

```kotlin [build.gradle.kts]
import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    baseDir = "."
    filePattern = "src/main/resources/**/*.bpmn"
    outputFolderPath = "src/main/kotlin"
    packagePath = "com.example.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}
```

```groovy [build.gradle]
import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask) {
    baseDir = "."
    filePattern = "src/main/resources/**/*.bpmn"
    outputFolderPath = "src/main/kotlin"
    packagePath = "com.example.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}
```

:::

All six properties are required. `baseDir` and `outputFolderPath` resolve against the project directory, so keep them relative: the task hashes them as plain strings, and an absolute path ties the up-to-date check and every build cache entry to one checkout location. See [Configuration](/guide/configuration) for every parameter, including the `generateBpmnModelJson` and `validateBpmnModels` tasks the plugin also registers.

## 3. The runtime dependency {#runtime-dependency}

Generated Kotlin and Java code imports types from `io.miragon:bpmn-to-code-runtime`. When the project applies the `java` plugin — which `java-library`, `application` and the Kotlin JVM plugin do — the plugin adds the runtime in its own version to `implementation`. Nothing to declare.

It adds nothing when the `java` plugin is absent. Declare the dependency yourself where the generated code is compiled without it, or in another project than the one that generates:

<!-- x-release-please-start-version -->
```kotlin
dependencies {
    implementation("io.miragon:bpmn-to-code-runtime:6.2.0")
}
```
<!-- x-release-please-end -->

C# output needs no runtime; its types are [inlined](/guide/generated-api#c-specifics).

## 4. Generate the API

```bash
./gradlew generateBpmnModelApi
```

The [generated files](/guide/generated-api) appear below `outputFolderPath`, in the directory of `packagePath`. Generation first runs the [built-in validation rules](/validate/) and fails on any error.

## 5. Generate as part of the build {#generate-as-part-of-the-build}

Gradle skips the task as `UP-TO-DATE` while nothing it depends on changed: the six properties, the plugin version, and the relative path and content of every BPMN file matching `filePattern`. Timestamps do not matter. Force a run with `./gradlew generateBpmnModelApi --rerun`.

Where the output goes decides what else Gradle tracks:

| `outputFolderPath` | Gradle additionally tracks | Effect |
|--------------------|----------------------------|--------|
| below the build directory | the generated directory as output | deleted or edited generated files are regenerated; [build cache](https://docs.gradle.org/current/userguide/build_cache.html); task dependency via `srcDir` |
| anywhere else, e.g. `src/main/kotlin` | nothing | deleted or edited generated files stay as they are until a BPMN file changes or you pass `--rerun`; no build cache |

A source folder is not declared as output: Gradle would fail every task reading it (sources jar, linters, documentation) unless each one depends on the generation.

To keep generated code out of version control, generate into the build directory and hand the task to the source set. Gradle then runs it before everything that reads the sources, without a `dependsOn`:

::: code-group

```kotlin [build.gradle.kts]
val generateBpmnModelApi = tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    outputFolderPath = "build/generated/bpmn"
    // ...
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() })
}
```

```groovy [build.gradle]
def generateBpmnModelApi = tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask) {
    outputFolderPath = "build/generated/bpmn"
    // ...
}

sourceSets.main.kotlin.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() })
```

:::

`srcDir(generateBpmnModelApi)` also works, but makes the `packagePath` directory the source root: a sources jar then contains `BikeLeasingProcessApi.kt` at its top level instead of under `com/example/...`.

If you commit the generated code instead, [verify it in CI](/guide/verify-in-ci).

## Several tasks

One task reads one set of files for one engine, one language and one package. Register a task per group when a project mixes engines, languages or packages:

```kotlin
tasks.register<GenerateBpmnModelsTask>("generateC7Api") {
    baseDir = "."
    filePattern = "src/main/resources/c7/*.bpmn"
    outputFolderPath = "src/main/kotlin"
    packagePath = "com.example.c7"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.CAMUNDA_7
}

tasks.register<GenerateBpmnModelsTask>("generateZeebeApi") {
    baseDir = "."
    filePattern = "src/main/resources/c8/*.bpmn"
    outputFolderPath = "src/main/kotlin"
    packagePath = "com.example.c8"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}
```

Give each task its own package: tasks generating into the same package [delete each other's files](/guide/generated-api#shared-definitions). When generating into the build directory, also avoid a package nested in another task's package (`com.example` and `com.example.c8`), which stops Gradle from caching the outer task.

## Filtering files

A task reads every file matching `filePattern`; there is no exclude. To leave files out, collect the ones you want with a [`Copy`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.Copy.html) task and generate from that directory:

```kotlin
val collectBpmnFiles = tasks.register<Copy>("collectBpmnFiles") {
    from("src/main/resources") {
        include("**/*.bpmn")
        exclude("**/draft-*.bpmn")
    }
    into(layout.buildDirectory.dir("bpmn-staging"))
}

tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    dependsOn(collectBpmnFiles)
    baseDir = "build/bpmn-staging"
    filePattern = "**/*.bpmn"
    // ...
}
```

## Next steps

- [Generated API](/guide/generated-api): what the files contain and how to use them.
- [Modeling](/guide/modeling): how ids and variable declarations in the model shape the API.
- [AI Skills](/skills/): `setup-bpmn-to-code-gradle` configures the plugin for you, `migrate-to-bpmn-to-code-apis` replaces hardcoded strings with the generated API.
- Example project: [easy-zeebe](https://github.com/emaarco/easy-zeebe), a Zeebe service built on the Gradle plugin.
