# bpmn-to-code Gradle plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.miragon.bpmn-to-code-gradle?label=Gradle%20Plugin%20Portal&color=335DE5)](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle)

**Generate the typed Process API from your BPMN models in your Gradle build.**

The Gradle plugin of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) turns element ids, job types and message names into constants for your workers and tests. It needs JDK 21 or newer.

## Install

<!-- x-release-please-start-version -->
```kotlin
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "7.0.1"
}
```
<!-- x-release-please-end -->

When the project applies the `java` plugin, the plugin adds `io.miragon:bpmn-to-code-runtime` in its own version.

## Get started

Point the generation task at your models in `build.gradle.kts`; all six properties are required:

```kotlin
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

Then run `./gradlew generateBpmnModelApi`.

## Tasks

| Task                   | Does                                                        |
| ---------------------- | ----------------------------------------------------------- |
| `generateBpmnModelApi` | Generates the Process API from BPMN files                   |
| `validateBpmnModels`   | Validates BPMN files without generating code (experimental) |

No task is wired into the build lifecycle; run them, or generate into the build directory and hand the task to the source set as the [Gradle guide](https://miragon.github.io/bpmn-to-code/getting-started/gradle) shows.

## Help and feedback

- **Setup:** [Gradle guide](https://miragon.github.io/bpmn-to-code/getting-started/gradle)
- **All parameters:** [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](../LICENSE)
