# bpmn-to-code

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.miragon.bpmn-to-code-gradle?label=Gradle%20Plugin%20Portal&color=335DE5)](https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle)
[![Maven Central](https://img.shields.io/maven-central/v/io.miragon/bpmn-to-code-maven?label=Maven%20Central&color=335DE5)](https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven)
[![GitHub release](https://img.shields.io/github/v/release/Miragon/bpmn-to-code?label=CLI&color=335DE5)](https://github.com/Miragon/bpmn-to-code/releases/latest)
[![Docker Hub](https://img.shields.io/docker/v/miragon/bpmn-to-code-web?sort=semver&label=Docker%20Hub&color=335DE5)](https://hub.docker.com/r/miragon/bpmn-to-code-web)
[![License: MIT](https://img.shields.io/github/license/Miragon/bpmn-to-code)](LICENSE)

**Rename a task in the modeler, and your build tells you which code still uses the old name.**

bpmn-to-code generates a typed Process API from your BPMN models, so workers, engine calls and tests use constants instead of strings for element ids, job types and message names.

## Why

A BPMN model and the code around it drift apart quietly: someone renames a task in the modeler, and workers, engine calls and tests keep their old strings until a process instance gets stuck. bpmn-to-code turns the model into a typed API, so the drift shows up in your build.

## What you get

**Constants instead of strings in your code.** Element ids, job types, messages, signals, errors and variables come from the model, see [Generated API](https://miragon.github.io/bpmn-to-code/guide/generated-api).

```kotlin
@JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
fun sendContract() { ... }
```

**Process tests that follow the model.** [`ProcessPath`](https://miragon.github.io/bpmn-to-code/guide/process-path) walks the expected route through the generated flow nodes; a path the model no longer contains does not compile.

```kotlin
val path = ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
    .then { it.serviceTaskValidateApplication }
    .then { it.businessRuleTaskCheckCreditRating }
    .then { it.gatewayIsSolvent }

assertThat(instance).hasPassedInOrder(*path.ids)
```

**Models checked before code is generated.** Built-in rules stop the generation on a missing job type or message name, and on the JVM `bpmn-to-code-testing` runs them and your own rules in JUnit, see [Validation](https://miragon.github.io/bpmn-to-code/validate/).

**No lock-in.** The API is plain constants and objects, independent of engine, worker library and test framework.

## Supported engines and languages

Works with **Camunda 8 (Zeebe)**, **Camunda 7** and **Operaton**. Generates **Kotlin** and **Java**, and as experimental output **C#** and **TypeScript**. All values are listed under [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration).

## Install

Pick the client that fits your project; each one has its own setup guide.

| Client              | For                                             | Setup                                                             |
| ------------------- | ----------------------------------------------- | ----------------------------------------------------------------- |
| Gradle plugin       | Kotlin and Java projects with Gradle            | [bpmn-to-code-gradle](bpmn-to-code-gradle/README.md)              |
| Maven plugin        | Java and Kotlin projects with Maven             | [bpmn-to-code-maven](bpmn-to-code-maven/README.md)                |
| CLI                 | any build, CI scripts, Homebrew                 | [bpmn-to-code-cli](bpmn-to-code-cli/README.md)                    |
| Web app             | trying it out, no build tool                    | [bpmn-to-code-web](bpmn-to-code-web/README.md)                    |
| MSBuild / .NET tool | C# projects (experimental, unpublished)         | [BpmnToCode.MSBuild](packages/nuget/BpmnToCode.MSBuild/README.md) |
| npm package         | TypeScript projects (experimental, unpublished) | [@miragon/bpmn-to-code](packages/npm/bpmn-to-code/README.md)      |

## Get started

1. Set up the client for your build from the table above.
2. Generate the API, for example with `./gradlew generateBpmnModelApi` or `mvn generate-sources`.
3. Replace the strings in your workers, engine calls and tests with the generated constants.

To fail CI on a stale API, see [Verify in CI](https://miragon.github.io/bpmn-to-code/guide/verify-in-ci).

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
