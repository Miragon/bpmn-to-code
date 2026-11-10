# bpmn-to-code

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.miragon.bpmn-to-code-gradle?label=Gradle%20Plugin%20Portal&color=335DE5)](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle)
[![Maven Central](https://img.shields.io/maven-central/v/io.miragon/bpmn-to-code-maven?label=Maven%20Central&color=335DE5)](https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven)
[![GitHub release](https://img.shields.io/github/v/release/Miragon/bpmn-to-code?label=CLI&color=335DE5)](https://github.com/Miragon/bpmn-to-code/releases/latest)
[![Docker Hub](https://img.shields.io/docker/v/miragon/bpmn-to-code-web?sort=semver&label=Docker%20Hub&color=335DE5)](https://hub.docker.com/r/miragon/bpmn-to-code-web)
[![License: MIT](https://img.shields.io/github/license/Miragon/bpmn-to-code)](LICENSE)

**Rename a task in the modeler, and your build tells you which code still uses the old name.**

bpmn-to-code generates a typed Process API from your BPMN models, so workers, engine calls and tests use constants instead of strings for element ids, job types and message names.

## Why

A worker that carries its job type as a string keeps compiling when the model renames it, and the process instance just waits. With the generated constant, the build fails at the worker instead:

```kotlin
// before: copied from the modeler
@JobWorker(type = "miravelo.sendContract")

// after: generated from the model
@JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
```

## What you get

- **One typed API per process.** Element ids, job types, messages, signals, errors and variables as constants, see [Generated API](https://miragon.github.io/bpmn-to-code/guide/generated-api).
- **Process tests that fail at compile time.** [`ProcessPath`](https://miragon.github.io/bpmn-to-code/guide/process-path) builds the expected path from generated flow nodes, so a renamed element breaks the test before it runs.
- **Models checked before code is generated.** Built-in rules stop the generation on a missing job type or message name, see [Validation](https://miragon.github.io/bpmn-to-code/validate/).
- **Your modeling rules as tests.** On the JVM, `bpmn-to-code-testing` runs the built-in rules and your own in JUnit, see [Testing module](https://miragon.github.io/bpmn-to-code/validate/testing).
- **No lock-in.** Plain constants and objects, independent of engine, worker library and test framework.

## Supported engines and languages

| Engine            | `processEngine` |
| ----------------- | --------------- |
| Camunda 8 / Zeebe | `ZEEBE`         |
| Camunda 7         | `CAMUNDA_7`     |
| Operaton          | `OPERATON`      |

| Language   | `outputLanguage` | Status       |
| ---------- | ---------------- | ------------ |
| Kotlin     | `KOTLIN`         | stable       |
| Java       | `JAVA`           | stable       |
| C#         | `CSHARP`         | experimental |
| TypeScript | `TYPESCRIPT`     | experimental |

C# and TypeScript come from the CLI, the web app and the build plugins; their NuGet and npm packages are not yet published, see [.NET](https://miragon.github.io/bpmn-to-code/getting-started/dotnet) and [TypeScript](https://miragon.github.io/bpmn-to-code/getting-started/typescript).

## Install

Your project needs Java 21 or newer.

- **Gradle:** add the plugin; with the `java` plugin applied it also adds `bpmn-to-code-runtime`, the types the generated code imports.

  <!-- x-release-please-start-version -->
  ```kotlin
  plugins {
      id("io.miragon.bpmn-to-code-gradle") version "7.0.1"
  }
  ```
  <!-- x-release-please-end -->

- **Maven:** add the runtime and the plugin.

  <!-- x-release-please-start-version -->
  ```xml
  <dependency>
      <groupId>io.miragon</groupId>
      <artifactId>bpmn-to-code-runtime</artifactId>
      <version>7.0.1</version>
  </dependency>

  <plugin>
      <groupId>io.miragon</groupId>
      <artifactId>bpmn-to-code-maven</artifactId>
      <version>7.0.1</version>
  </plugin>
  ```
  <!-- x-release-please-end -->

- **Command line:** `brew install miragon/tap/bpmn-to-code`, or a zip from the [latest release](https://github.com/Miragon/bpmn-to-code/releases/latest), see [bpmn-to-code-cli](bpmn-to-code-cli/README.md).
- **Web app:** [in the browser](https://bpmn-to-code.miragon.io/static/index.html), or self-hosted from the [Docker image](https://hub.docker.com/r/miragon/bpmn-to-code-web).

## Get started

**Gradle with Kotlin**

1. Point the generation task at your models in `build.gradle.kts`:

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

2. Run `./gradlew generateBpmnModelApi`. It is not part of `build` unless you [wire it in](https://miragon.github.io/bpmn-to-code/getting-started/gradle).
3. Replace the strings in your workers and tests with the constants in `com.example.process`.

**Maven with Java**

1. Inside the plugin, bind the goal and point it at your models:

   ```xml
   <executions>
       <execution>
           <phase>generate-sources</phase>
           <goals><goal>generate-bpmn-api</goal></goals>
       </execution>
   </executions>
   <configuration>
       <baseDir>${project.basedir}</baseDir>
       <filePattern>src/main/resources/*.bpmn</filePattern>
       <outputFolderPath>${project.basedir}/src/main/java</outputFolderPath>
       <packagePath>com.example.process</packagePath>
       <outputLanguage>JAVA</outputLanguage>
       <processEngine>ZEEBE</processEngine>
   </configuration>
   ```

2. Run `mvn generate-sources`.
3. Replace the strings in your workers and tests with the constants in `com.example.process`.

Every option is in [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration); to fail CI on a stale API, see [Verify in CI](https://miragon.github.io/bpmn-to-code/guide/verify-in-ci).

## With your AI assistant

For Kotlin and Java, the Claude Code plugin sets up the generator and writes workers, engine calls and tests against the Process API, see [Agent Skills](https://miragon.github.io/bpmn-to-code/skills/):

```text
/plugin marketplace add Miragon/bpmn-to-code
/plugin install bpmn-to-code@bpmn-to-code
```

## Help and feedback

- **Documentation:** every feature, option and limit at [miragon.github.io/bpmn-to-code](https://miragon.github.io/bpmn-to-code/).
- **Upgrading:** release notes and migration guides in the [changelog](https://miragon.github.io/bpmn-to-code/changelog/).
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues).

## Contributing

How to build, test and change the project is in [CONTRIBUTING.md](CONTRIBUTING.md). We do not accept external pull requests.

## License

[MIT](LICENSE) © Marco Schäck. Built and maintained by [Miragon](https://miragon.io).
