# bpmn-to-code-gradle

Gradle plugin of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code), published to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle).

<!-- x-release-please-start-version -->
```kotlin
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "6.2.0"
}
```
<!-- x-release-please-end -->

| Task | Does |
|---|---|
| `generateBpmnModelApi` | Generates the Process API from BPMN files |
| `generateBpmnModelJson` | Generates the process JSON |
| `validateBpmnModels` | Validates BPMN files without generating code (experimental) |

No task is wired into the build lifecycle; run or wire them yourself. The plugin adds `io.miragon:bpmn-to-code-runtime` in its own version to projects that apply the `java` plugin.

Setup and all parameters: [Gradle guide](https://miragon.github.io/bpmn-to-code/getting-started/gradle) and [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration).
