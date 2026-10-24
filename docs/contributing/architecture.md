# Architecture

Which module does what and how core is layered. The reasons are recorded in the [Architecture Decisions](./adr/); this page describes what is there.

## Modules

Gradle modules (`settings.gradle.kts`):

| Module | What it is | Published to |
|---|---|---|
| `bpmn-to-code-core` | Reads BPMN, validates, generates the Process API and the process JSON | Not published. Its classes are packed into the Gradle plugin, Maven plugin and testing jars; the web app and the command-line tool depend on it directly. |
| `bpmn-to-code-gradle` | Gradle plugin `io.miragon.bpmn-to-code-gradle` | Gradle Plugin Portal |
| `bpmn-to-code-maven` | Maven plugin `io.miragon:bpmn-to-code-maven` (mojos written in Java) | Maven Central |
| `bpmn-to-code-runtime` | Dependency-free types the generated Kotlin and Java code refers to, plus `ProcessPath` | Maven Central |
| `bpmn-to-code-testing` | `BpmnValidator`, the test library for validation rules | Maven Central |
| `bpmn-to-code-web` | Ktor server with a static frontend | Docker Hub (`miragon/bpmn-to-code-web`) |
| `bpmn-to-code-cli` | Command-line tool `bpmn-to-code` with the commands `generate-api` and `validate`; `./gradlew :bpmn-to-code-cli:cliArchive` packs it with a jlink runtime for the current platform | GitHub release assets, one zip per platform, and Homebrew `miragon/tap/bpmn-to-code` (experimental) |
| `bpmn-to-code-architecture-tests` | Konsist tests that enforce the rules on this page | Not published |
| `bpmn-to-code-benchmark` | Hand-run JMH comparison with a released version, see [Benchmarking](./benchmark) | Not published |

Outside the Gradle build:

| Folder | What it is |
|---|---|
| `bpmn-to-code-skills` | Claude Code plugin for users of bpmn-to-code, installed through the marketplace manifest in `.claude-plugin/` |
| `shared/bpmn` | The MiraVelo BPMN models every module tests against, see [Tests](./#tests) |
| `tools` | bpmnlint setup for those models |
| `packages/nuget` | The .NET tool `Miragon.BpmnToCode` (a C# launcher that starts the CLI jars on the installed Java), the C# runtime package `Miragon.BpmnToCode.Runtime` and the MSBuild integration `Miragon.BpmnToCode.MSBuild`, packed by `publish-to-nuget.yml`, and `BpmnToCode.Runtime.Tests`, which compiles generated C# against the runtime package |
| `packages/npm` | The npm package `@miragon/bpmn-to-code` with its launcher, which starts the CLI jars on the installed Java, and the TypeScript runtime package `@miragon/bpmn-to-code-runtime` with tests that type-check and run generated TypeScript against it, staged by `publish-to-npm.yml` |
| `docs` | This site |

How the four output languages reach the JVM, .NET and Node: [Languages and Ecosystems](./languages).

All published artifacts share one version, `projectVersion` in `gradle.properties`. Why core is embedded instead of published: [ADR 001](./adr/001-product-surfaces-and-distribution).

## Layers of core

Core is a hexagon under `io.miragon.bpmn`. `HexagonalArchitectureTest` allows exactly these dependencies:

| Package | Holds | May depend on |
|---|---|---|
| `domain` | Process model, validation rules, domain services | Kotlin, Java, kotlin-logging, itself |
| `application.port.inbound` | Use cases (`…UseCase`, `…Query`) | `domain` |
| `application.port.outbound` | Ports (`…Port`, `…Repository`) | `domain` |
| `application.service` | One `…Service` per inbound port | `domain`, both port packages, `adapter.outbound` |
| `adapter.outbound` | BPMN reading (`engine`), code generation (`codegen`), `json`, `filesystem` | `domain`, `application.port.outbound` |
| `adapter.inbound` | The `…Plugin` classes, one per use case | `domain`, `application.port.inbound`, `application.service` |

There is no dependency-injection framework, hence these further rules:

- A service or plugin takes its collaborators as constructor parameters typed as ports; the concrete implementation appears only as default value.
- A service implements exactly one inbound port and does not call another service. That is why the filesystem and the in-memory variant of a use case are two services; both hand the models to `BpmnValidationService` and the whole run to one port call, so they differ only in reading and writing.
- All ports are interfaces.

The other modules see core only through `domain` and `adapter.inbound`. `ExternalModuleImportTest` enforces that for the Gradle plugin, the Maven plugin, the web app and the command-line tool. `domain` is also what users compile custom validation rules against, so changes there are public API changes ([ADR 011](./adr/011-compatibility-policy)).

`CodingGuidelinesTest` adds two rules for every module: a file declares at most one top-level type, and a file that declares a type declares nothing else at top level.

Why it looks like this: [ADR 002](./adr/002-hexagonal-core).
