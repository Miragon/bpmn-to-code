# Gradle

The plugin `io.miragon.bpmn-to-code-gradle` comes from the Gradle Plugin Portal and needs JDK 21+. It **registers** `generateBpmnModelApi` and `validateBpmnModels` itself. Configure them with `tasks.named(...)`; a second `tasks.register` with the same name fails the build.

## Plugin and generation task

```kotlin
// build.gradle.kts
import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

plugins {
    id("io.miragon.bpmn-to-code-gradle") version "<version>"
}

tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    baseDir = "."
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "src/main/kotlin"
    packagePath = "io.miragon.example.adapter.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}
```

- All six properties are required.
- Keep `baseDir` and `outputFolderPath` **relative**. They resolve against the project directory, and an absolute path ties the build cache to one checkout.
- **Groovy DSL** (`build.gradle`): `tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask) { … }` with the same assignments and imports.
- If `settings.gradle.kts` declares `pluginManagement.repositories`, it must include `gradlePluginPortal()`.

## Runtime dependency

Nothing to add. The plugin adds `io.miragon:bpmn-to-code-runtime` to `implementation` once the `java` plugin is applied. The Kotlin JVM, `java-library` and `application` plugins all apply it. Declare it yourself only where generated code is compiled in another project.

## Hook into the build

**Committed in the source folder** (`outputFolderPath = "src/main/kotlin"`): run generation before compiling.

```kotlin
tasks.named("compileKotlin") { dependsOn("generateBpmnModelApi") }  // compileJava for Java
```

**In the build directory**: hand the task to the source set, and Gradle runs it before everything that reads the sources.

```kotlin
val generateBpmnModelApi = tasks.named("generateBpmnModelApi", GenerateBpmnModelsTask::class) {
    outputFolderPath = "build/generated/bpmn"
    // the other five properties as above
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() })
}
```

For Java: `sourceSets.main { java.srcDir(generateBpmnModelApi.map { it.outputFolderPath.get() }) }`.

## Several engines or packages

One task covers one engine, one language and one package. For more, register additional tasks under **new** names, and give each its own package:

```kotlin
tasks.register<GenerateBpmnModelsTask>("generateZeebeApi") { /* same six properties */ }
```

## Model validation test (optional)

```kotlin
dependencies {
    testImplementation("io.miragon:bpmn-to-code-testing:<version>")
}
```

```kotlin
// src/test/kotlin/<package>/adapter/process/model/<Process>ModelValidationTest.kt
class BikeLeasingModelValidationTest {

    @Test
    fun `the bpmn models satisfy all rules`() {
        BpmnValidator
            .fromClasspath("bpmn/")
            .engine(ProcessEngine.ZEEBE)
            .withRules(BpmnRules.all())
            .validate()
            .assertNoViolations()
    }
}
```

Imports: `io.miragon.bpmn.testing.BpmnValidator`, `io.miragon.bpmn.testing.BpmnRules`, `io.miragon.bpmn.domain.shared.ProcessEngine`. The `fromClasspath` path is the resource folder that holds the models. A Java project writes the same test as a JUnit class with `@DisplayName`.

## Verify

```bash
./gradlew generateBpmnModelApi
./gradlew test --tests '*ModelValidationTest'
```
