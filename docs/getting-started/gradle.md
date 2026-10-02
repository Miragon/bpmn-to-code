# 🚀 Gradle Setup

The bpmn-to-code Gradle plugin generates type-safe Process API files from your BPMN models as part of your Gradle build. It's available on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle) and takes just a few minutes to set up.

## 1. Apply the plugin

<!-- x-release-please-start-version -->
::: code-group

```kotlin [build.gradle.kts]
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "5.2.0"
}
```

```groovy [build.gradle]
plugins {
    id 'io.miragon.bpmn-to-code-gradle' version '5.2.0'
}
```

:::
<!-- x-release-please-end -->

Make sure the Gradle Plugin Portal is in your `settings.gradle.kts`:

::: code-group

```kotlin [settings.gradle.kts]
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
```

```groovy [settings.gradle]
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
```

:::

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

See [Configuration](/guide/configuration) for all available parameters.

## 3. Generate the API

```bash
./gradlew generateBpmnModelApi
```

The generated Process API file(s) will appear in your configured output folder.

## 4. Generate as part of the build

Gradle skips the generation tasks as `UP-TO-DATE` when nothing they depend on changed since the last run: the
task configuration (`baseDir`, `filePattern`, `outputFolderPath`, `packagePath`, `outputLanguage`, `processEngine`,
`enableVariants`), the plugin version, and the relative path and content of every BPMN file matching `filePattern`. File
timestamps do not matter. Force a run with `./gradlew generateBpmnModelApi --rerun`.

To keep generated code out of version control, generate into the build directory and hand the task to the
source set — Gradle then runs it before everything that reads the sources, without a `dependsOn`:

::: code-group

```kotlin [build.gradle.kts]
import io.miragon.bpmn.adapter.GenerateBpmnModelsTask

val generateBpmnModelApi = tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    outputFolderPath = "build/generated/bpmn"
    // ...
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() })
}
```

```groovy [build.gradle]
import io.miragon.bpmn.adapter.GenerateBpmnModelsTask

def generateBpmnModelApi = tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask) {
    outputFolderPath = "build/generated/bpmn"
    // ...
}

sourceSets.main.kotlin.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() })
```

:::

`srcDir(generateBpmnModelApi)` also works, but makes the `packagePath` directory the source root: a sources jar
then contains `OrderProcessApi.kt` at its top level instead of under `com/example/...`.

Where the output goes decides what else Gradle tracks:

| `outputFolderPath` | Gradle additionally tracks | Effect |
|--------------------|----------------------------|--------|
| below the build directory | the generated directory as output | deleted or edited generated files are regenerated; [build cache](https://docs.gradle.org/current/userguide/build_cache.html); task dependency via `srcDir` |
| anywhere else, e.g. `src/main/kotlin` | nothing | deleted or edited generated files stay as they are until a BPMN file changes or you pass `--rerun`; no build cache |

A source folder is not declared as output: Gradle would fail every task reading it (sources jar, linters,
documentation) unless each one depends on the generation.

Keep `baseDir` and `outputFolderPath` relative (they resolve against the project directory): a relative value
stays out of the build cache key, so a cache shared across machines or across checkouts at different paths (such
as Git worktrees) still matches. An absolute value ties each entry to its location.

## 5. Automated setup with AI Skills

Using [Claude Code](https://docs.anthropic.com/en/docs/claude-code)? The `setup-bpmn-to-code-gradle` skill can configure the plugin for you automatically — it detects your project structure, finds your BPMN files, and adds the right configuration.

After setup, use the `migrate-to-bpmn-to-code-apis` skill to replace hardcoded BPMN strings across your codebase with references to the generated Process API.

```bash
npx skills add https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills/setup-bpmn-to-code-gradle
npx skills add https://github.com/Miragon/bpmn-to-code/tree/main/bpmn-to-code-skills/skills/migrate-to-bpmn-to-code-apis
```

See [AI Skills](/skills/) for all available skills.

## 6. Advanced configuration

Need multiple engines, separate packages per domain, or file filtering? See [Gradle Advanced Configuration](/getting-started/gradle-advanced).
